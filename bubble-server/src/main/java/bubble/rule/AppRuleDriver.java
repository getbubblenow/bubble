package bubble.rule;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.AppRuleHarness;
import bubble.resources.stream.FilterMatchersRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.InputStream;
import java.util.Map;

import static org.cobbzilla.util.io.StreamUtil.stream2bytes;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public interface AppRuleDriver {

    AppRuleDriver getNext();
    void setNext(AppRuleDriver next);
    default boolean hasNext() { return getNext() != null; }

    default void init(JsonNode config,
                      JsonNode userConfig,
                      AppRule rule,
                      AppMatcher matcher,
                      Account account,
                      Device device) {}

    default boolean preprocess(AppRuleHarness ruleHarness, FilterMatchersRequest filter, Account account, Request req, ContainerRequest request) {
        return false;
    }

    default InputStream filterRequest(InputStream in) {
        if (hasNext()) return doFilterRequest(getNext().filterRequest(in));
        return doFilterRequest(in);
    }

    default InputStream doFilterRequest(InputStream in) { return in; }

    default InputStream filterResponse(InputStream in) {
        if (hasNext()) return doFilterResponse(getNext().filterRequest(in));
        return doFilterResponse(in);
    }

    default InputStream doFilterResponse(InputStream in) { return in; }

    default String resolveResource(String res, Map<String, Object> ctx) {
        final String resource = locateResource(res);
        if (resource == null || resource.trim().length() == 0) return "";

        final Handlebars handlebars = getHandlebars();
        if (handlebars == null) return resource;

        final String resolved = HandlebarsUtil.apply(handlebars, resource, ctx);
        return resolved == null ? "" : resolved;
    }

    default Handlebars getHandlebars() { return null; }

    default String locateResource(String res) {
        if (!res.startsWith("@")) return res;
        final String prefix = getPackagePath(getClass()) + "/" + getClass().getSimpleName();
        final String path = res.substring(1);
        switch (res) {
            case "@css": case "@js":
                try { return stream2string(prefix + "." + path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "." + path); } catch (Exception e) {}
                break;
            case "@body":
                try { return stream2string(prefix + "_body.html.hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "_body.html"); } catch (Exception e) {}
                break;
            default:
                try { return stream2string(prefix + "_" + path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "_" + path ); } catch (Exception e) {}
                try { return stream2string(path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(path); } catch (Exception e) {}
        }
        return null;
    }

    default byte[] locateBinaryResource(String res) {
        if (res.startsWith("@")) res = res.substring(1);
        final String prefix = getPackagePath(getClass()) + "/" + getClass().getSimpleName();
        try { return stream2bytes(prefix + "_" + res ); } catch (Exception e) {}
        try { return stream2bytes(res); } catch (Exception e) {}
        return null;
    }

}
