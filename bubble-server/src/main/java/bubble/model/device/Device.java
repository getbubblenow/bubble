package bubble.model.device;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleNetwork;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.validation.constraints.Size;

import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true) @ECTypeCreate(method="DISABLED")
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "network", "name"}),
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"network", "name"})
})
public class Device extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = { "uuid", "name", "enabled", "totpKey" };
    public static final String[] UPDATE_FIELDS = { "name", "enabled" };

    public static final String UNINITIALIZED_DEVICE = "__uninitialized_device__";
    public static final String UNINITIALIZED_DEVICE_LIKE = UNINITIALIZED_DEVICE+"%";

    public Device (Device other) { copy(this, other, CREATE_FIELDS); }

    public Device (String uuid) { setUuid(uuid); }

    public static Device newUninitializedDevice(String networkUuid, String accountUuid) {
        return new Device()
                .setName(UNINITIALIZED_DEVICE+randomUUID().toString())
                .setNetwork(networkUuid)
                .setAccount(accountUuid)
                .initTotpKey();
    }

    public static Device firstDeviceForNewNetwork(BubbleNetwork network) {
        return new Device(randomUUID().toString())
                .setName(UNINITIALIZED_DEVICE+randomUUID().toString())
                .setNetwork(network.getUuid())
                .setAccount(network.getAccount())
                .initTotpKey();
    }

    @Override public Identifiable update(Identifiable thing) {
        copy(this, thing, UPDATE_FIELDS);
        return this;
    }

    public void initialize (Device other) {
        copy(this, other);
        initTotpKey();
    }

    @ECSearchable(filter=true) @ECField(index=10)
    @ECIndex @Column(nullable=false, length=500)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    public boolean uninitialized () { return name.startsWith(UNINITIALIZED_DEVICE); }
    public boolean initialized () { return !uninitialized(); }

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=BubbleNetwork.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @Size(max=300, message="err.totpKey.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(300+ENC_PAD)+") NOT NULL")
    @JsonIgnore @Getter @Setter private String totpKey;
    public Device initTotpKey() { return setTotpKey(randomAlphanumeric(200)); }

    // make ctime visible
    @JsonProperty public long getCtime () { return super.getCtime(); }

}
