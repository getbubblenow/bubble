/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.message;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import bubble.service.message.AppMessageService;
import bubble.service.message.MessageResourceFormat;
import bubble.service.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.*;
import static bubble.service.message.MessageService.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Path(MESSAGES_ENDPOINT)
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Service @Slf4j
public class MessagesResource {

    @Autowired private MessageService messageService;
    @Autowired private AppMessageService appMessageService;
    @Autowired private BubbleConfiguration configuration;

    private final Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();

    @DELETE
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_UTILITY,
            summary="Flush message cache",
            description="Flush message cache",
            responses=@ApiResponse(responseCode=SC_OK, description="empty JSON object indicates success")
    )
    public Response flushMessageCache (@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        messageCache.clear();
        return ok_empty();
    }

    @GET @Path("/{locale}/{group}")
    @Operation(tags=API_TAG_UTILITY,
            summary="Get localized messages",
            description="Get localized messages by group. `locale` specifies the desired locale. If the locale is not supported, another similar locale or the default locale will be used. `The `group` param is the message group to retrieve. Groups are: `pre_auth`, `post_auth`, `countries`, `timezones`, `apps`. Requesting the `post_auth` or `apps` groups requires a valid API session. `format` is an optional format for the messages. Format can be `raw` or `underscore` (which converts dots to underscores). Default is `underscore`.",
            parameters={
                @Parameter(name="locale", description="The desired locale. If the locale is not supported, another similar locale or the default locale will be used", required=true),
                @Parameter(name="group", description="Message group to retrieve. Groups are: `pre_auth`, `post_auth`, `countries`, `timezones`, `apps`. Requesting the `post_auth` or `apps` groups requires a valid API session.", required=true),
                @Parameter(name="format", description="Format for the messages. Format can be `raw` or `underscore` (which converts dots to underscores). Default is `underscore`.")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="status of the router, which can be one of: `none`, `active`, `unreachable`, `deleted`")
    )
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

    private Map<String, String> loadMessages(Account caller, String locale, String group, MessageResourceFormat format) {

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
                props = messageService.loadMessages(locale, group);
            }
            final Map<String, String> messages = messageService.formatMessages(props, format);
            messageCache.put(cacheKey, messages);
        }
        return messageCache.get(cacheKey);
    }

}
