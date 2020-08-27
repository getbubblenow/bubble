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
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.util.Map;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class FilterAppMessagesResource {

    public static final String PARAM_FILTER_MESSAGE = "find";

    private final Account account;
    private final BubbleApp app;

    @Autowired private AppMessageDAO appMessageDAO;

    public FilterAppMessagesResource(Account account, BubbleApp app) {
        this.account = account;
        this.app = app;
    }

    private final ExpirationMap<String, AppMessage> singleMessageCache
            = new ExpirationMap<>(10, HOURS.toMillis(1), ExpirationEvictionPolicy.atime);

    @GET @Path("/{locale}")
    public AppMessage find(@Context ContainerRequest ctx,
                           @PathParam("locale") String locale) {
        final Map<String, String> params = queryParams(ctx.getRequestUri().getQuery());
        final String filter = params.get(PARAM_FILTER_MESSAGE);
        final String cacheKey = app.getUuid() + ":" + locale + ":" + filter;
        return singleMessageCache.computeIfAbsent(cacheKey, k -> {
            final AppMessage messages = appMessageDAO.findByAccountAndAppAndLocale(account.getUuid(), app.getUuid(), locale);
            return new AppMessage().setMessages(messages.getMessages(splitAndTrim(filter, ",")));
        });
    }

}
