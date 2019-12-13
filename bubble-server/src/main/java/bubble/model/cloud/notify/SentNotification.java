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

import static bubble.ApiConstants.EP_SENT_NOTIFICATIONS;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_SENT_NOTIFICATIONS, listFields={"type"})
@ECTypeFields(list={"type"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class SentNotification extends NotificationBase {

    @ECIndex @Column(nullable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationSendStatus status = NotificationSendStatus.created;

}
