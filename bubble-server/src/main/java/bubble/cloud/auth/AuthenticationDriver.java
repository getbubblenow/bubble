/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.auth;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAccountMessageService;
import bubble.service.message.MessageService;
import com.github.jknack.handlebars.Handlebars;
import org.apache.commons.collections4.map.SingletonMap;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.io.FileUtil.basename;
import static org.cobbzilla.util.io.StreamUtil.stream2string;

public interface AuthenticationDriver extends CloudServiceDriver {

    Logger log = LoggerFactory.getLogger(AuthenticationDriver.class);

    String CTX_LOCALE = "locale";

    default BubbleConfiguration getConfiguration() { return die("getConfiguration: not implemented!"); }
    default String getTemplatePath() { return die("getTemplatePath: not implemented!"); }

    default String getLocale() { return getConfiguration().getThisNetwork().getLocale(); }
    default BubbleNodeDAO getNodeDAO() { return getConfiguration().getBean(BubbleNodeDAO.class); }
    default BubbleNetworkDAO getNetworkDAO() { return getConfiguration().getBean(BubbleNetworkDAO.class); }

    boolean send(Account account, AccountMessage message, AccountContact contact);

    boolean send(RenderedMessage renderedMessage);

    @Override default boolean test () { return true; }

    static <T> T bean(BubbleConfiguration configuration, Class<T> beanClass) {
        return configuration.getBean(beanClass);
    }

    default Map<String, Object> buildContext(Account account, AccountMessage message, AccountContact contact) {
        return buildContext(account, message, contact, getConfiguration());
    }

    static Map<String, Object> buildContext(Account account,
                                            AccountMessage message,
                                            AccountContact contact,
                                            BubbleConfiguration configuration) {
        final BubbleConfiguration c = configuration;
        final String locale = account.getLocale();
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("account", account);
        ctx.put("message", message);
        ctx.put("contact", contact);
        ctx.put("configuration", c);
        ctx.put("support", c.getSupport().forLocale(locale));
        ctx.put("appLinks", c.getAppLinks().forLocale(locale));

        final AccountPolicy policy = bean(c, AccountPolicyDAO.class).findSingleByAccount(account.getUuid());
        account.setPolicy(policy);
        ctx.put("confirmationToken", bean(c, StandardAccountMessageService.class).confirmationToken(policy, message, contact));

        final Map<String, String> messages = bean(c, MessageService.class).formatStandardMessages(locale);
        final BubbleNode node = getNode(message, c);
        final BubbleNetwork network = getNetwork(message, c);
        if (message.getAction() == AccountAction.first_payment) {
            final AccountPlan accountPlan = getAccountPlan(network, c);
            if (accountPlan != null) {
                final BubblePlan plan = bean(c, BubblePlanDAO.class).findByUuid(accountPlan.getPlan());
                final Bill bill = bean(c, BillDAO.class).findFirstUnpaidByAccountPlan(accountPlan.getUuid());
                final AccountPaymentMethod paymentMethod = bean(c, AccountPaymentMethodDAO.class).findByUuid(accountPlan.getPaymentMethod());
                if (paymentMethod == null) return die("buildContext: paymentMethod "+accountPlan.getPaymentMethod()+" not found for accountPlan: "+accountPlan.getUuid());

                final CloudService paymentService = bean(c, CloudServiceDAO.class).findByUuid(paymentMethod.getCloud());
                if (paymentService == null) return die("buildContext: payment cloud "+paymentMethod.getCloud()+" not found for paymentMethod: "+paymentMethod.getUuid()+", accountPlan: "+accountPlan.getUuid());

                final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(c);
                final long amountDue = paymentDriver.amountDue(accountPlan.getUuid(), bill.getUuid(), paymentMethod.getUuid());

                ctx.put("plan", plan);
                ctx.put("now", now());
                ctx.put("nextBillAmount", amountDue);
                ctx.put("nextBillDate", plan.getPeriod().periodMillis(bill.getPeriodStart()));
                ctx.put("billPeriodUnit", messages.get("price_period_"+plan.getPeriod().name()+"_unit"));
                ctx.put("billDateFormat", messages.get("label_date").replace("{", "").replace("}", ""));
                ctx.put("timezone", network.getTimezone());
                ctx.put("currencySymbol", messages.get("currency_symbol_"+plan.getCurrency()));
                ctx.put("currencyDecimal", messages.get("currency_decimal_"+plan.getCurrency()));
            }
        }
        ctx.put("node", node);
        ctx.put("network", network);
        ctx.put("publicUri", network.getPublicUri(c));
        return ctx;
    }

    default BubbleNode getNode(AccountMessage message) { return getNode(message, getConfiguration()); }

    static BubbleNode getNode(AccountMessage message, BubbleConfiguration configuration) {
        switch (message.getTarget()) {
            case account: case network: return configuration.getThisNode();
            default: return null;
        }
    }

    static BubbleNetwork getNetwork(AccountMessage message, BubbleConfiguration configuration) {
        final BubbleNetworkDAO networkDAO = bean(configuration, BubbleNetworkDAO.class);
        return networkDAO.findByUuid(message.getNetwork());
    }

    static AccountPlan getAccountPlan(BubbleNetwork network, BubbleConfiguration configuration) {
        return bean(configuration, AccountPlanDAO.class).findByAccountAndNetwork(network.getAccount(), network.getUuid());
    }

    default String render(String basename, Map<String, Object> ctx, AccountMessage message) {
        return render(basename, ctx, message, getConfiguration(), getTemplatePath());
    }

    static String render(String basename, Map<String, Object> ctx, AccountMessage message,
                         BubbleConfiguration configuration, String templatePath) {
        return _render(loadTemplate(templatePath, message.templateName(basename), configuration.getThisNetwork().getLocale(), configuration), ctx, configuration);
    }

    static String _render(String template, Map<String, Object> ctx, BubbleConfiguration configuration) {
        return _render(template, ctx, configuration.getHandlebars());
    }

    static String _render(String template, Map<String, Object> ctx, Handlebars handlebars) {
        return HandlebarsUtil.apply(handlebars, template, ctx);
    }

    static String loadTemplate(String templatePath, String templateName, String locale, BubbleConfiguration configuration) {
        final Handlebars handlebars = configuration.getHandlebars();
        final String path = localePath(locale, templatePath, handlebars) + "/" + templateName;
        try {
            return stream2string(path);
        } catch (Exception e) {
            final String defaultLocale = configuration.getDefaultLocale();
            if (locale.equals(defaultLocale)) {
                return handleTemplateNotFound(templateName, path);
            }
            final String defaultPath = localePath(defaultLocale, templatePath, handlebars) + "/" + templateName;
            try {
                return stream2string(defaultPath);
            } catch (Exception e2) {
                return handleTemplateNotFound(templateName, path);
            }
        }
    }

    Map<String, String> _localePaths = new ConcurrentHashMap<>();

    static String localePath(final String locale, String templatePath, Handlebars hbs) {
        final String key = locale+":"+templatePath;
        return _localePaths.computeIfAbsent(key,
                k -> HandlebarsUtil.apply(hbs, templatePath, new SingletonMap<>(CTX_LOCALE, locale), '[', ']'));
    }

    String HTML_MESSAGE_HBS = "htmlMessage.hbs";
    String[] OPTIONAL_TEMPLATES = {HTML_MESSAGE_HBS};
    static String[] getOptionalTemplates() { return OPTIONAL_TEMPLATES; }

    static String handleTemplateNotFound(String templateName, String path) {
        if (ArrayUtils.contains(getOptionalTemplates(), basename(templateName))) {
            log.warn("loadTemplate: no (optional) default template found: "+path);
            return null;
        } else {
            return die("loadTemplate: no default template found: " + path);
        }
    }

}
