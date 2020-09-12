/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static bubble.ApiConstants.EP_FLEX_ROUTERS;
import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity
@ECType(root=true) @ToString(of={"ip", "port"})
@ECTypeURIs(baseURI=EP_FLEX_ROUTERS, listFields={"name", "enabled"})
@NoArgsConstructor @Accessors(chain=true) @Slf4j
@ECIndexes({ @ECIndex(unique=true, of={"account", "ip"}) })
public class FlexRouter extends IdentifiableBase implements HasAccount {

    public static final String[] UPDATE_FIELDS = { "enabled", "active", "auth_token", "token", "key", "host_key" };
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "ip", "port");

    public FlexRouter (FlexRouter other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Device.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String device;

    @ECSearchable(filter=true) @ECField(index=30)
    @ECIndex @Column(nullable=false, updatable=false, length=50)
    @Getter @Setter private String ip;

    @JsonIgnore @Transient public String getName () { return getIp(); }

    @ECField(index=40) @HasValue(message="err.sshPublicKey.required")
    @Type(type=ENCRYPTED_STRING)  @Column(updatable=false, columnDefinition="varchar("+(10000+ENC_PAD)+") NOT NULL")
    @Getter private String key;
    public boolean hasKey () { return !empty(key); }
    public FlexRouter setKey(String k) {
        if (k == null) k = "";
        this.key = k.trim();
        if (!empty(key)) this.keyHash = sha256_hex(key);
        return this;
    }

    @ECField(index=50)
    @ECIndex(unique=true) @Column(length=100, updatable=false, nullable=false)
    @Getter @Setter private String keyHash;
    public boolean hasKeyHash () { return !empty(keyHash); };

    @ECSearchable(filter=true) @ECField(index=60)
    @ECIndex(unique=true) @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer port;
    public boolean hasPort () { return port != null && port > 1024; }

    public String id () { return getIp() + "/" + getUuid(); }

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return bool(enabled); }

    @ECSearchable @ECField(index=80)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean initialized = true;
    public boolean initialized() { return bool(initialized); }
    public boolean uninitialized() { return !initialized(); }

    @ECSearchable @ECField(index=90)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean active = true;
    public boolean active() { return bool(active); }
    public boolean inactive() { return !active(); }

    @ECSearchable(filter=true) @ECField(index=100)
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @JsonIgnore @Getter @Setter private String token;
    public boolean hasToken () { return !empty(token); }

    // used for sending the token, we never send it back
    @Transient @Getter @Setter private String auth_token;
    public boolean hasAuthToken () { return !empty(auth_token); }

    // used for sending the SSH host key to flexrouter
    @Transient @Getter @Setter private String host_key;

    public FlexRouterPing pingObject() { return new FlexRouterPing(this); }
    public String proxyBaseUri() { return "http://127.0.0.1:" + getPort(); }

}
