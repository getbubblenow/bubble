package bubble.model.device;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleNetwork;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndexes;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECType;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.validation.constraints.Size;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "network", "name"}),
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"network", "name"})
})
public class Device extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = { "uuid", "name", "enabled", "totpKey" };
    public static final String[] UPDATE_FIELDS = { "name", "enabled" };

    public static final String UNINITIALIZED_DEVICE = "__uninitialized_device__";

    public Device (Device other) { copy(this, other, CREATE_FIELDS); }

    public Device (String uuid) { setUuid(uuid); }

    @Override public Identifiable update(Identifiable thing) {
        copy(this, thing, UPDATE_FIELDS);
        return this;
    }

    public void initialize (Device other) {
        copy(this, other);
        initTotpKey();
    }

    @ECIndex @Column(nullable=false, length=500)
    @Getter @Setter private String name;

    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    public boolean uninitialized () { return name.equals(UNINITIALIZED_DEVICE); }
    public boolean initialized () { return !uninitialized(); }

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=BubbleNetwork.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @Size(max=300, message="err.totpKey.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+300+ENC_PAD+") NOT NULL")
    @Getter @Setter private String totpKey;
    public Device initTotpKey() { return setTotpKey(randomAlphanumeric(200)); }

}
