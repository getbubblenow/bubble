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
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static bubble.ApiConstants.ERROR_MAXLEN;
import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.ellipsis;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@MappedSuperclass @NoArgsConstructor @Accessors(chain=true)
public class NotificationBase extends IdentifiableBase implements HasAccountNoName {

    // synchronous requests may include this
    @ECSearchable @ECField(index=10)
    @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String notificationId;
    public boolean hasId () { return notificationId != null; }

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true) @ECField(index=30)
    @ECIndex @Column(nullable=false, updatable=false, length=50)
    @Enumerated(EnumType.STRING) @Getter @Setter private NotificationType type;

    @Getter @Setter private boolean resolveNodes = false;

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=BubbleNode.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String fromNode;
    public boolean hasFromNode () { return fromNode != null; }

    @ECSearchable @ECField(index=50)
    @ECForeignKey(entity=BubbleNode.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String toNode;

    @ECSearchable(filter=true) @ECField(index=60)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(1024+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String uri;

    @ECSearchable(filter=true) @ECField(index=70)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100_000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String payloadJson;
    public boolean hasPayload () { return payloadJson != null; }

    @Transient public JsonNode getPayload () { return json(payloadJson, JsonNode.class); }
    public <T extends NotificationBase> T setPayload (JsonNode payload) { return (T) setPayloadJson(json(payload)); }

    @Transient @JsonIgnore public BubbleNode getNode() { return json(payloadJson, BubbleNode.class); }

    @ECField(index=80)
    @Getter @Setter private Boolean truncated = false;
    public boolean truncated () { return truncated != null && truncated; }

    @ECField(index=90)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(1000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String receiptJson;

    @Transient public NotificationReceipt getReceipt () { return receiptJson == null ? null : json(receiptJson, NotificationReceipt.class); }
    public <T extends NotificationBase> T setReceipt (NotificationReceipt receipt) { return (T) setReceiptJson(receipt == null ? null : json(receiptJson)); }

    @ECSearchable(filter=true) @ECField(index=100)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(ERROR_MAXLEN+ENC_PAD)+")")
    @JsonIgnore @Getter private String error;
    public NotificationBase setError (String err) { this.error = ellipsis(err, ERROR_MAXLEN); return this; }
    public void setException(Exception e) { setError(errorString(e, ERROR_MAXLEN)); }

}
