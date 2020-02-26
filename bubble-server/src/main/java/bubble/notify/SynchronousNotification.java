/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify;

import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.json.JsonSerializableException;

import java.util.UUID;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public abstract class SynchronousNotification {

    @Getter @Setter private String id = UUID.randomUUID().toString();

    @Getter @Setter private JsonNode response;
    public boolean hasResponse () { return response != null; }

    @Getter @Setter private JsonSerializableException exception;
    public boolean hasException () { return exception != null; }

    public String getCacheKey (BubbleNode delegate, NotificationType type) {
        return hashOf(delegate.getUuid(), type, getClass().getName(), getCacheKey());
    }

    protected abstract String getCacheKey();

}
