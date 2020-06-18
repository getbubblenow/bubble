/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.message;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import bubble.service.message.AppMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.string.StringUtil;
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
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
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

    public static final String APPS_MESSAGE_GROUP = "apps";
    public static final String[] ALL_MESSAGE_GROUPS
            = ArrayUtil.append(PRE_AUTH_MESSAGE_GROUPS, "post_auth", APPS_MESSAGE_GROUP);

    @Autowired private AppMessageService appMessageService;
    @Autowired private BubbleConfiguration configuration;

    private Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();

    @DELETE
    public Response flushMessageCache (@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        messageCache.clear();
        return ok_empty();
    }

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

        if (log.isDebugEnabled()) log.debug("loadMessagesByGroup: finding messages for group="+group+" among locales: "+StringUtil.toString(locales));
        for (String loc : locales) {
            try {
                return ok_utf8(loadMessages(caller, loc, group, format));
            } catch (Exception e) {
                log.debug("loadMessagesByGroup: error loading group "+group+" for locale "+loc+": "+e, e);
            }
        }
        log.error("loadMessagesByGroup: error loading group "+group+" for any locale: "+locales);
        return notFound(locale+"/"+group);
    }

    private Map<String, String> loadMessages(Account caller, String locale, String group, MessageResourceFormat format) throws IOException {

        final boolean isAppsGroup = group.equalsIgnoreCase(APPS_MESSAGE_GROUP);
        if (isAppsGroup && caller == null) {
            if (log.isDebugEnabled()) log.debug("loadMessages: returning empty app messages for caller=null");
            return Collections.emptyMap();
        }
        locale = normalizeLocale(locale);

        final String cacheKey = (isAppsGroup ? caller.getUuid()+"/" : "") + locale + "/" + group + "/" + format;
        if (!messageCache.containsKey(cacheKey)) {
            final Properties props;
            if (isAppsGroup) {
                if (log.isDebugEnabled()) log.debug("loadMessages: loading app messages for caller="+caller.getEmail()+", locale="+locale);
                props = appMessageService.loadAppMessages(caller, locale);
                if (log.isDebugEnabled()) log.debug("loadMessages: loaded app messages for caller="+caller.getEmail()+", locale="+locale+", props.size="+props.size());
            } else {
                props = new Properties();
                props.load(new BufferedReader(new InputStreamReader(loadResourceAsStream(MESSAGE_RESOURCE_BASE + locale + MESSAGE_RESOURCE_PATH + group + "/" + RESOURCE_MESSAGES_PROPS), UTF8cs)));
            }
            final Map<String, String> messages = new LinkedHashMap<>();
            props.forEach((key, value) -> messages.put(format.format(key.toString()), value.toString()));
            messageCache.put(cacheKey, messages);
        }
        return messageCache.get(cacheKey);
    }

    private String normalizeLocale(String locale) {
        if (empty(locale)) return locale;
        final int uPos = locale.indexOf('_');
        if (uPos == -1) return locale;
        if (uPos == locale.length()-1) return locale.substring(locale.length()-1);
        return locale.substring(0, uPos).toLowerCase()+'_'+locale.substring(uPos+1).toUpperCase();
    }

}
