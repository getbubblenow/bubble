/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.app.AppMessageDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMessage;
import bubble.model.app.BubbleApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.stream.UrlSendableResource;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.API_TAG_APP_RUNTIME;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.CONTENT_TYPE_ANY;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class FilterAppMessagesResource {

    public static final String FILTER_PREFIX = "prefix:";

    private final Account account;
    private final BubbleApp app;

    @Autowired private AppMessageDAO appMessageDAO;

    public FilterAppMessagesResource(Account account, BubbleApp app) {
        this.account = account;
        this.app = app;
    }

    private final ExpirationMap<String, AppMessage> singleMessageCache
            = new ExpirationMap<>(10, HOURS.toMillis(1), ExpirationEvictionPolicy.atime);

    @GET @Path("/{locale}/find/{filter}")
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="Find matching message for an app",
            description="Find messages for an app that match a filter. The `filter` param can be a comma-separated list of keys to return messages for, or can start with \"prefix:\" to indicate to return all messages whose keys have this prefix. Return an AppMessage object with the `messages` field containing the matched messages",
            parameters={
                @Parameter(name="locale", description="desired locale"),
                @Parameter(name="filter", description="only return messages matching this filter")
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an AppMessage object with the `messages` field containing the matched messages"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="device not found")
            }
    )
    public AppMessage find(@Context ContainerRequest ctx,
                           @PathParam("locale") String locale,
                           @PathParam("filter") String filter) {
        final String cacheKey = app.getUuid() + ":" + locale + ":" + filter;
        return singleMessageCache.computeIfAbsent(cacheKey, k -> {
            final AppMessage messages = appMessageDAO.findByAccountAndAppAndLocale(account.getUuid(), app.getUuid(), locale);
            return filter.startsWith(FILTER_PREFIX)
                    ? new AppMessage().setMessages(messages.getMessagesWithPrefix(filter.substring(FILTER_PREFIX.length())))
                    : new AppMessage().setMessages(messages.getMessages(splitAndTrim(filter, ",")));
        });
    }

    private final ExpirationMap<String, String> linkMessageCache
            = new ExpirationMap<>(10, MINUTES.toMillis(5), ExpirationEvictionPolicy.atime);

    @GET @Path("/{locale}/link/{link}")
    @Produces(CONTENT_TYPE_ANY)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="Load a link and return the data",
            description="Load a link (from a message) and return the data. You cannot use this to load any link, only links specified by message keys for the app",
            parameters={
                    @Parameter(name="locale", description="desired locale"),
                    @Parameter(name="link", description="the name of the message key containing the link")
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the data contained by the link")
            }
    )
    public Response loadLink(@Context ContainerRequest ctx,
                             @PathParam("locale") String locale,
                             @PathParam("link") String link) {
        final String cacheKey = app.getUuid() + ":" + locale + ":" + link;
        return send(new UrlSendableResource(linkMessageCache.computeIfAbsent(cacheKey, k -> {
            final AppMessage messages = appMessageDAO.findByAccountAndAppAndLocale(account.getUuid(), app.getUuid(), locale);
            final String url = messages.getMessage(link);
            if (url == null) throw notFoundEx(link);
            if (!isHttpOrHttps(url)) throw invalidEx("err.url.invalid", "not a valid URL: "+url, url);
            return url;
        })));
    }

}
