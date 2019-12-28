package bubble.resources.message;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.collection.ArrayUtil;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(MESSAGES_ENDPOINT)
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Service @Slf4j
public class MessagesResource {

    public static final String MESSAGE_RESOURCE_PATH = "/server/";
    public static final String RESOURCE_MESSAGES_PROPS = "ResourceMessages.properties";

    public static final String[] PRE_AUTH_MESSAGE_GROUPS = {"pre_auth", "countries", "timezones"};
    public static final String[] ALL_MESSAGE_GROUPS = ArrayUtil.append(PRE_AUTH_MESSAGE_GROUPS, "post_auth");

    @Autowired private BubbleConfiguration configuration;

    @GET @Path("/{locale}/{group}")
    public Response loadMessagesByGroup(@Context ContainerRequest ctx,
                                        @PathParam("locale") String locale,
                                        @PathParam("group") String group,
                                        @QueryParam("format") MessageResourceFormat format) {
        final String defaultLocale = configuration.getDefaultLocale();
        final List<String> locales = new ArrayList<>();
        if (locale.equals(DETECT_LOCALE)) {
            locales.addAll(getLocales(ctx, defaultLocale));
        } else {
            locales.add(locale);
            if (!locale.equals(defaultLocale)) locales.add(defaultLocale);
        }
        final Account caller = optionalUserPrincipal(ctx);
        if (caller == null && !ArrayUtils.contains(PRE_AUTH_MESSAGE_GROUPS, group)) return forbidden();

        if (!ArrayUtils.contains(ALL_MESSAGE_GROUPS, group)) return notFound(group);
        if (format == null) format = MessageResourceFormat.underscore;

        for (String loc : locales) {
            try {
                return ok_utf8(loadMessages(loc, group, format));
            } catch (Exception e) {
                log.debug("loadMessagesByGroup: error loading group "+group+" for locale "+loc+": "+e);
            }
        }
        log.error("loadMessagesByGroup: error loading group "+group+" for any locale: "+locales);
        return notFound(locale+"/"+group);
    }

    private Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();

    private Map<String, String> loadMessages(String locale, String group, MessageResourceFormat format) throws IOException {
        final String cacheKey = locale+"/"+group+"/"+format;
        if (!messageCache.containsKey(cacheKey)) {
            final Properties props = new Properties();
            props.load(new BufferedReader(new InputStreamReader(loadResourceAsStream(MESSAGE_RESOURCE_BASE + locale + MESSAGE_RESOURCE_PATH + group + "/" + RESOURCE_MESSAGES_PROPS), UTF8cs)));
            final Map<String, String> messages = new LinkedHashMap<>();
            props.forEach((key, value) -> messages.put(format.format(key.toString()), value.toString()));
            messageCache.put(cacheKey, messages);
        }
        return messageCache.get(cacheKey);
    }

}
