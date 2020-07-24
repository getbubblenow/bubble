package bubble.model.account;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true) @Slf4j
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({@ECIndex(unique=true, of={"account", "trustId"})})
public class TrustedClient extends IdentifiableBase implements HasAccount {

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECField(index=20)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @JsonIgnore @Getter @Setter private String trustId;

    @JsonIgnore @Transient
    @Override public String getName() { return getTrustId(); }

    public boolean isValid(TrustedClientLoginRequest request) {
        return sha256_hex(request.getTrustSalt()+"-"+trustId).equals(request.getTrustHash());
    }

}
