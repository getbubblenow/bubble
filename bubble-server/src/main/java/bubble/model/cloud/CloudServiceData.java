package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_DATA;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_DATA, listFields={"key", "data", "expiration"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"cloud", "key"}) })
public class CloudServiceData extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = {"account", "key", "data", "expiration"};
    public static final String[] VALUE_FIELDS = {"data", "expiration"};

    public CloudServiceData (CloudServiceData other) { copy(this, other, CREATE_FIELDS); }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @Override @JsonIgnore @Transient public String getName() { return getKey(); }

    @ECSearchable
    @ECForeignKey(entity=CloudService.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String cloud;

    @ECSearchable(filter=true)
    @HasValue(message="err.key.required")
    @ECIndex @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String key;
    public boolean hasKey () { return key != null; }

    @ECSearchable(filter=true)
    @Size(max=100000, message="err.data.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @Getter @Setter private String data;

    @Transient public JsonNode getDataJson () { return data == null ? null : json(data, JsonNode.class); }
    public CloudServiceData setDataJson(JsonNode n) { return setData(n == null ? null : json(n)); }

    @ECSearchable
    @ECIndex @Getter @Setter private Long expiration;

}
