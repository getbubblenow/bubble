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

import static bubble.ApiConstants.EP_RECEIVED_NOTIFICATIONS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(baseURI=EP_RECEIVED_NOTIFICATIONS, listFields={"type"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class ReceivedNotification extends NotificationBase {

    public ReceivedNotification(SentNotification notification) { copy(this, notification); setUuid(null); }

    @ECSearchable(filter=true) @ECField(index=1000)
    @ECIndex @Column(nullable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationProcessingStatus processingStatus = NotificationProcessingStatus.received;

}
