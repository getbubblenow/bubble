package bubble.model.cloud.notify;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import bubble.model.cloud.BubbleNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;

import javax.persistence.*;

import static bubble.ApiConstants.ERROR_MAXLEN;
import static org.cobbzilla.util.json.JsonUtil.json;

@MappedSuperclass @NoArgsConstructor @Accessors(chain=true)
public class NotificationBase extends IdentifiableBase implements HasAccountNoName {

    // synchronous requests may include this
    @ECSearchable
    @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String notificationId;
    public boolean hasId () { return notificationId != null; }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @ECIndex @Column(nullable=false, updatable=false, length=50)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationType type;

    @Getter @Setter private boolean resolveNodes = false;

    @ECSearchable
    @ECForeignKey(entity=BubbleNode.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String fromNode;
    public boolean hasFromNode () { return fromNode != null; }

    @ECSearchable
    @ECForeignKey(entity=BubbleNode.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String toNode;

    @ECSearchable(filter=true)
    @Column(nullable=false, updatable=false, length=1024)
    @Getter @Setter private String uri;

    @ECSearchable(filter=true)
    @Column(updatable=false, length=100_000)
    @JsonIgnore @Getter @Setter private String payloadJson;
    public boolean hasPayload () { return payloadJson != null; }

    @Transient public JsonNode getPayload () { return json(payloadJson, JsonNode.class); }
    public <T extends NotificationBase> T setPayload (JsonNode payload) { return (T) setPayloadJson(json(payload)); }

    @Transient @JsonIgnore public BubbleNode getNode() { return json(payloadJson, BubbleNode.class); }

    @Getter @Setter private Boolean truncated = false;
    public boolean truncated () { return truncated != null && truncated; }

    @Column(length=1000)
    @JsonIgnore @Getter @Setter private String receiptJson;

    @Transient public NotificationReceipt getReceipt () { return receiptJson == null ? null : json(receiptJson, NotificationReceipt.class); }
    public <T extends NotificationBase> T setReceipt (NotificationReceipt receipt) { return (T) setReceiptJson(receipt == null ? null : json(receiptJson)); }

    @ECSearchable(filter=true)
    @Column(length=ERROR_MAXLEN)
    @JsonIgnore @Getter @Setter private String error;

    public void setException(Exception e) {
        setError(ZillaRuntime.errorString(e, ERROR_MAXLEN));
    }

}
