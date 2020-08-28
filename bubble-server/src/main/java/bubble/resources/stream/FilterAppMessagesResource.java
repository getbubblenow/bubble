/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.app.AppMessageDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMessage;
import bubble.model.app.BubbleApp;
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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
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
