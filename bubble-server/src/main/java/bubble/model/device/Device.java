/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import bubble.ApiConstants;
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

import javax.persistence.*;
import javax.validation.constraints.Size;

import java.io.File;

import static bubble.ApiConstants.EP_DEVICES;
import static bubble.model.device.BubbleDeviceType.other;
import static bubble.model.device.BubbleDeviceType.uninitialized;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true)
@ECTypeURIs(baseURI=EP_DEVICES, listFields={"name", "enabled"})
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "network", "name"}),
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"network", "name"})
})
public class Device extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = { UUID, "name", "enabled", "totpKey", "deviceType" };
    public static final String[] UPDATE_FIELDS = { "name", "enabled", "securityLevel" };

    public static final String UNINITIALIZED_DEVICE = "__uninitialized_device__";
    public static final String UNINITIALIZED_DEVICE_LIKE = UNINITIALIZED_DEVICE+"%";

    public static final String VPN_CONFIG_PATH = ApiConstants.HOME_DIR + "/configs/localhost/wireguard/";

    public File qrFile () { return new File(Device.VPN_CONFIG_PATH+getUuid()+".png"); }
    public File vpnConfFile () { return new File(Device.VPN_CONFIG_PATH+getUuid()+".conf"); }
    public boolean configsOk () { return qrFile().exists() && vpnConfFile().exists(); }

    public Device (Device other) { copy(this, other, CREATE_FIELDS); }

    public Device (String uuid) { setUuid(uuid); }

    @Override public void beforeCreate() { if (!hasUuid()) super.beforeCreate(); }

    public static Device newUninitializedDevice(String networkUuid, String accountUuid) {
        return new Device()
                .setName(UNINITIALIZED_DEVICE+randomUUID().toString())
                .setNetwork(networkUuid)
                .setAccount(accountUuid)
                .setSecurityLevel(DeviceSecurityLevel.standard)
                .initTotpKey();
    }

    @Override public Identifiable update(Identifiable thing) { copy(this, thing, UPDATE_FIELDS); return this; }

    public Device initDeviceType() {
        if (empty(getDeviceType()) || getDeviceType().equals(uninitialized)) setDeviceType(other);
        return this;
    }

    @ECSearchable(filter=true) @ECField(index=10)
    @ECIndex @Column(nullable=false, length=500)
    @Getter @Setter private String name;

    public String id () { return getName() + "/" + getUuid(); }

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECIndex @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private BubbleDeviceType deviceType;

    @ECSearchable @ECField(index=40)
    @Enumerated(EnumType.STRING) @Column(length=30, nullable=false)
    @Getter @Setter private DeviceSecurityLevel securityLevel;
    public boolean hasSecurityLevel () { return securityLevel != null; }

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    public boolean uninitialized () { return name.startsWith(UNINITIALIZED_DEVICE); }
    public boolean initialized () { return !uninitialized(); }

    @ECSearchable @ECField(index=60)
    @ECForeignKey(entity=BubbleNetwork.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @Size(max=300, message="err.totpKey.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(300+ENC_PAD)+") NOT NULL")
    @JsonIgnore @Getter @Setter private String totpKey;
    public Device initTotpKey() { return hasTotpKey() ? this : setTotpKey(randomAlphanumeric(200)); }
    public boolean hasTotpKey() { return !empty(totpKey); }

    // make ctime visible
    @JsonProperty public long getCtime () { return super.getCtime(); }

}
