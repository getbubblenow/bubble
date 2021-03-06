/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import bubble.BubbleHandlebars;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.NonNull;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public interface CloudServiceDriver {

    String[] CLOUD_DRIVER_PACKAGE = new String[]{"bubble.cloud"};

    String CTX_API_KEY = "apiKey";
    String CTX_PARAMS = "params";

    default boolean disableDelegation () { return false; }

    @NonNull default CloudService setupDelegatedCloudService(@NonNull final BubbleConfiguration configuration,
                                                             @NonNull final CloudService parentService,
                                                             @NonNull final CloudService delegatedService) {
        return delegatedService.setDelegated(parentService.getUuid())
                               .setCredentials(CloudCredentials.delegate(configuration.getThisNode(), configuration))
                               .setTemplate(false);
    }

    default void postServiceDelete(@NonNull final CloudServiceDAO serviceDAO, @NonNull final CloudService service) {
        // noop
    }

    void setConfig(JsonNode json, CloudService cloudService);

    static <T extends CloudServiceDriver> T setupDriver(BubbleConfiguration configuration, T driver) {
        final T wired = configuration.autowire(driver);
        wired.postSetup();
        return wired;
    }

    default void postSetup() {}

    CloudCredentials getCredentials();
    void setCredentials(CloudCredentials creds);

    CloudServiceType getType();

    default Handlebars getHandlebars () { return BubbleHandlebars.instance.getHandlebars(); }

    default HttpResponseBean fetch(String url, Map<String, Object> ctx) {
        final HttpResponseBean response;
        try {
            // re-apply handlebars to apiKey if it still contains curlies
            if (ctx.containsKey(CTX_API_KEY) && ctx.get(CTX_API_KEY).toString().contains("{{")) {
                ctx.put(CTX_API_KEY, HandlebarsUtil.apply(getHandlebars(), ctx.get(CTX_API_KEY).toString(), ctx));
            }

            final String realUrl = HandlebarsUtil.apply(getHandlebars(), url, ctx);
            response = HttpUtil.getResponse(realUrl);
        } catch (Exception e) {
            return die("fetch: error fetching url: "+url+" : "+e);
        }
        if (!response.isOk()) {
            return die("fetch: unexpected HTTP status for url: "+url+" : " + response);
        }
        return response;
    }

    default <R> R valOrError(String val, Class<R> resultClass) {
        final JsonNode node = json(val, JsonNode.class);
        if (node.has("errorMessage")) {
            return die("error: "+node.get("errorClass").textValue()+": "+node.get("errorMessage").textValue());
        }
        return json(val, resultClass);
    }

    default boolean test(Object arg) { return test(); }
    boolean test();

}
