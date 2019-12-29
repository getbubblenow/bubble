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
import org.cobbzilla.util.handlebars.HandlebarsUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.StreamUtil.stream2string;

public interface AuthenticationDriver extends CloudServiceDriver {

    String CTX_LOCALE = "locale";

    default BubbleConfiguration getConfiguration() { return die("getConfiguration: not implemented!"); }
    default String getTemplatePath() { return die("getTemplatePath: not implemented!"); }

    default String getLocale() { return getConfiguration().getThisNetwork().getLocale(); }
    default BubbleNodeDAO getNodeDAO() { return getConfiguration().getBean(BubbleNodeDAO.class); }
    default BubbleNetworkDAO getNetworkDAO() { return getConfiguration().getBean(BubbleNetworkDAO.class); }

    boolean send(Account account, AccountMessage message, AccountContact contact);

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

        final AccountPolicy policy = configuration.getBean(AccountPolicyDAO.class).findSingleByAccount(account.getUuid());
        account.setPolicy(policy);
        ctx.put("confirmationToken", configuration.getBean(StandardAccountMessageService.class).confirmationToken(policy, message, contact));

        final BubbleNode node = getNode(message, configuration);
        ctx.put("node", node);
        ctx.put("network", getNetwork(message, node, configuration));
        return ctx;
    }

    default BubbleNode getNode(AccountMessage message) { return getNode(message, getConfiguration()); }

    static BubbleNode getNode(AccountMessage message, BubbleConfiguration configuration) {
        switch (message.getTarget()) {
            case account: case network: return configuration.getThisNode();
            case node: return configuration.getBean(BubbleNodeDAO.class).findByAccountAndId(message.getAccount(), message.getName());
            default: return null;
        }
    }

    default BubbleNetwork getNetwork(AccountMessage message, BubbleNode node) {
        return getNetwork(message, node, getConfiguration());
    }

    static BubbleNetwork getNetwork(AccountMessage message, BubbleNode node, BubbleConfiguration configuration) {
        final BubbleNetworkDAO networkDAO = configuration.getBean(BubbleNetworkDAO.class);
        switch (message.getTarget()) {
            case account: return configuration.getThisNetwork();
            case network: return networkDAO.findByAccountAndId(message.getAccount(), message.getName());
            case node: return networkDAO.findByAccountAndId(message.getAccount(), node.getNetwork());
            default: return null;
        }
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
                return die("loadTemplate: no default template found: "+path);
            }
            final String defaultPath = localePath(defaultLocale, templatePath, handlebars) + "/" + templateName;
            try {
                return stream2string(defaultPath);
            } catch (Exception e2) {
                return die("loadTemplate: no default template found: "+defaultPath);
            }
        }
    }

    Map<String, String> _localePaths = new ConcurrentHashMap<>();

    static String localePath(final String locale, String templatePath, Handlebars hbs) {
        final String key = locale+":"+templatePath;
        return _localePaths.computeIfAbsent(key,
                k -> HandlebarsUtil.apply(hbs, templatePath, new SingletonMap<>(CTX_LOCALE, locale), '[', ']'));
    }

}
