/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.auth;

import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.json.JsonUtil.json;

@Accessors(chain=true)
public class AuthDriverNotification extends SynchronousNotification {

    @Getter @Setter private String authService;
    @Getter @Setter private JsonNode renderedMessage;
    @Getter @Setter private String renderedMessageClass;

    @Getter(lazy=true) private final String cacheKey = hashOf(authService, renderedMessage != null ? json(renderedMessage) : null);

}
