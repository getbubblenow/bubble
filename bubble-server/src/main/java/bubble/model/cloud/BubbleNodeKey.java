/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.security.RsaKeyPair;
import org.cobbzilla.util.security.RsaMessage;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.io.File;
import java.util.List;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.RsaKeyPair.newRsaKeyPair;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true) @ECTypeCreate(method="DISABLED")
@NoArgsConstructor @Accessors(chain=true) @ToString(of={"publicKeyHash"}, callSuper=true)
public class BubbleNodeKey extends IdentifiableBase implements HasAccountNoName {

    public static final long TOKEN_GENERATION_LIMIT = DAYS.toMillis(1);

    public BubbleNodeKey(BubbleNode node) {
        setNode(node.getUuid());
        setAccount(node.getAccount());
        final RsaKeyPair keyPair = newRsaKeyPair();
        setPublicKey(keyPair.getPublicKey());
        setPrivateKey(keyPair.getPrivateKey());
        setRemoteHost(node.getIp4());
    }

    public BubbleNodeKey(String uuid, BubbleNode node, String publicKey, String remoteHost) {
        setUuid(uuid);
        setAccount(node.getAccount());
        setNode(node.getUuid());
        setPublicKey(publicKey);
        setRemoteHost(remoteHost);
    }

    private BubbleNodeKey(BubbleNodeKey key) { copy(this, key); }

    public static BubbleNodeKey sageMask(BubbleNodeKey sageKey) {
        return new BubbleNodeKey(sageKey)
                .setPrivateKey(null)
                .setPrivateKeyHash(null);
    }

    public static BubbleNodeKey nodeKeyFromFile(File file) {
        try {
            return fromJson(file, BubbleNodeKey.class);
        } catch (Exception e) {
            return die("nodeKeyFromFile: error parsing " + abs(file) + ": " + e);
        }
    }

    @Override public void beforeCreate() { if (!hasUuid()) super.beforeCreate(); }

    // Generate a new key if we have no keys, or no keys with an expiration > 24 hours from now
    public static boolean shouldGenerateNewKey(List<BubbleNodeKey> keys) {
        if (keys == null || keys.isEmpty()) return true;
        return keys.stream().allMatch(k -> k.expiresInLessThan(TOKEN_GENERATION_LIMIT));
    }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=BubbleNode.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String node;

    @ECSearchable(filter=true) @ECField(index=30, type=EntityFieldType.opaque_string)
    @Column(length=10000, updatable=false, nullable=false)
    @Getter private String publicKey;
    public BubbleNodeKey setPublicKey (String k) {
        this.publicKey = k;
        if (!empty(k)) this.publicKeyHash = sha256_hex(k);
        return this;
    }

    @ECField(index=40)
    @ECIndex(unique=true) @Column(length=100, updatable=false, nullable=false)
    @Getter @Setter private String publicKeyHash;

    @Size(max=10000, message="err.privateKey.length")
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @JsonIgnore @Getter private String privateKey;
    public BubbleNodeKey setPrivateKey (String k) {
        this.privateKey = k;
        this.privateKeyHash = k == null ? null : sha256_hex(k);
        return this;
    }
    public boolean hasPrivateKey () { return !empty(privateKey); }

    @Size(max=100, message="err.privateKeyHash.length")
    @Type(type=ENCRYPTED_STRING) @ECIndex(unique=true) @Column(updatable=false, columnDefinition="varchar("+(100+ENC_PAD)+")")
    @Getter @Setter private String privateKeyHash;

    @ECSearchable(filter=true) @ECField(index=50)
    @Size(max=100, message="err.remoteHost.length")
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String remoteHost;

    @ECSearchable(type=EntityFieldType.epoch_time) @ECField(index=60)
    @ECIndex @Column(nullable=false, updatable=false)
    @Getter @Setter private Long expiration = defaultExpiration();

    @JsonIgnore @Transient public long getExpirationMillis () { return expiration - now(); }

    public boolean valid() { return now() < getExpiration(); }
    public boolean expired() { return !valid(); }
    public boolean expiresInLessThan(long millis) { return getExpirationMillis() < millis; }

    public static long defaultExpiration() { return now() + (TOKEN_GENERATION_LIMIT*3); }

    public RsaMessage encrypt (String data, RsaKeyPair recipient) { return getRsaKey().encrypt(data, recipient); }
    public String decrypt (RsaMessage message, RsaKeyPair sender) { return getRsaKey().decrypt(message, sender); }

    @JsonIgnore @Transient
    @Getter(lazy=true) private final RsaKeyPair rsaKey = new RsaKeyPair()
            .setPublicKey(getPublicKey())
            .setPrivateKey(getPrivateKey());

}
