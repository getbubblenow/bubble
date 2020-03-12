/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.time.TimeUtil.formatISO8601;
import static org.cobbzilla.util.time.TimeUtil.parseISO8601;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;
import static org.cobbzilla.wizard.model.entityconfig.annotations.EntityFieldRequired.optional;

@Entity @ECType(root=true)
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"account", "sshPublicKeyHash"})
})
public class AccountSshKey extends IdentifiableBase implements HasAccount {

    public static final String[] UPDATE_FIELDS = {"expiration", "installSshKey"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "sshPublicKey");

    public AccountSshKey (AccountSshKey other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=100)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECField(index=10) @HasValue(message="err.sshPublicKey.required")
    @Type(type=ENCRYPTED_STRING)  @Column(updatable=false, columnDefinition="varchar("+(10000+ENC_PAD)+") NOT NULL")
    @Getter private String sshPublicKey;
    public AccountSshKey setSshPublicKey(String k) {
        this.sshPublicKey = k;
        if (!empty(k)) this.sshPublicKeyHash = sha256_hex(k);
        return this;
    }
    public boolean hasSshPublicKey () { return !empty(sshPublicKey); }

    @ECField(index=40)
    @ECIndex(unique=true) @Column(length=100, updatable=false, nullable=false)
    @Getter @Setter private String sshPublicKeyHash;
    public boolean hasSshPublicKeyHash () { return !empty(sshPublicKeyHash); };

    @ECSearchable @ECField(index=50, required=optional)
    @Column(nullable=false)
    @Getter @Setter private Boolean installSshKey = false;
    public boolean installSshKey() { return bool(installSshKey); }

    @ECField(index=60)
    @Getter @Setter private Long expiration;
    public boolean hasExpiration () { return expiration != null; }
    public boolean neverExpires () { return !hasExpiration(); }
    public boolean valid() { return neverExpires() || now() < getExpiration(); }
    public boolean expired() { return !valid(); }

    @Transient public String getExpirationISO8601 () {
        return hasExpiration() ? formatISO8601(getExpiration()) : null;
    }
    public AccountSshKey setExpirationISO8601 (String isoDate) {
        setExpiration(empty(isoDate) ? null : parseISO8601(isoDate));
        return this;
    }
}
