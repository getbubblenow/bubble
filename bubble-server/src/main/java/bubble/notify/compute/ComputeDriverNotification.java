/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.compute;

import bubble.model.cloud.BubbleNode;
import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class ComputeDriverNotification extends SynchronousNotification {

    @Getter @Setter private BubbleNode node;
    @Getter @Setter private String computeService;

    public ComputeDriverNotification(BubbleNode node) { this.node = node; }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(node != null ? node.getUuid() : null, computeService);

}
