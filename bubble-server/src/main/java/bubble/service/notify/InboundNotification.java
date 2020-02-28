/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.notify;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.security.RsaMessage;

@NoArgsConstructor @Accessors(chain=true)
public class InboundNotification {

    @Getter @Setter private RsaMessage message;
    @Getter @Setter private String remoteHost;
    @Getter @Setter private String fromNodeUuid;
    @Getter @Setter private String fromKeyUuid;
    @Getter @Setter private String toKeyUuid;
    @Getter @Setter private String restoreKey;

}
