package bubble.model.cloud.notify;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECTypeFields;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECTypeURIs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import static bubble.ApiConstants.EP_RECEIVED_NOTIFICATIONS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_RECEIVED_NOTIFICATIONS, listFields={"type"})
@ECTypeFields(list={"type"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class ReceivedNotification extends NotificationBase {

    public ReceivedNotification(SentNotification notification) { copy(this, notification); setUuid(null); }

    @ECIndex @Column(nullable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationProcessingStatus processingStatus = NotificationProcessingStatus.received;

}
