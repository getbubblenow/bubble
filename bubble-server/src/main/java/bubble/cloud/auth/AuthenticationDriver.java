/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.auth;

import bubble.cloud.CloudServiceDriver;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAccountMessageService;
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

    default Map<String, Object> buildContext(Account account, AccountMessage message, AccountContact contact) {
        return buildContext(account, message, contact, getConfiguration());
    }

    static Map<String, Object> buildContext(Account account, AccountMessage message, AccountContact contact,
                                            BubbleConfiguration configuration) {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("account", account);
        ctx.put("message", message);
        ctx.put("contact", contact);
        ctx.put("configuration", configuration);
        ctx.put("support", configuration.getSupport().forLocale(account.getLocale()));
        ctx.put("appLinks", configuration.getAppLinks().forLocale(account.getLocale()));

        final AccountPolicy policy = configuration.getBean(AccountPolicyDAO.class).findSingleByAccount(account.getUuid());
        account.setPolicy(policy);
        ctx.put("confirmationToken", configuration.getBean(StandardAccountMessageService.class).confirmationToken(policy, message, contact));

        final BubbleNode node = getNode(message, configuration);
        final BubbleNetwork network = getNetwork(message, configuration);
        ctx.put("node", node);
        ctx.put("network", network);
        ctx.put("publicUri", network.getPublicUri(configuration));
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
        final BubbleNetworkDAO networkDAO = configuration.getBean(BubbleNetworkDAO.class);
        return networkDAO.findByUuid(message.getNetwork());
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
