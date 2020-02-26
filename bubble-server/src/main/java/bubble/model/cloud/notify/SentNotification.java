/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.cloud.notify;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import static bubble.ApiConstants.EP_SENT_NOTIFICATIONS;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(baseURI=EP_SENT_NOTIFICATIONS, listFields={"type"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class SentNotification extends NotificationBase {

    @ECSearchable @ECField(index=1000)
    @ECIndex @Column(nullable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationSendStatus status = NotificationSendStatus.created;

}
