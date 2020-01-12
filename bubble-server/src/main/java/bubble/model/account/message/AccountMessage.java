package bubble.model.account.message;

import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.HasAccount;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Size;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECIndexes({
        @ECIndex(of={"account", "name", "requestId", "messageType", "action", "target"}),
        @ECIndex(of={"account", "name", "messageType", "action", "target"}),
        @ECIndex(of={"messageType", "action", "target"})
})
@NoArgsConstructor @Accessors(chain=true) @Slf4j @ToString(of={"messageType", "action", "target", "name"})
public class AccountMessage extends IdentifiableBase implements HasAccount {

    public AccountMessage (AccountMessage other) { copy(this, other); setUuid(null); }

    @ECForeignKey(entity=Account.class) @ECField(index=10)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String account;

    @ECIndex @ECField(index=20) @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String requestId = randomUUID().toString();

    @ECIndex @ECField(index=30) @Column(length=UUID_MAXLEN, updatable=false)
    @Getter @Setter private String contact;
    public boolean hasContact () { return !empty(contact); }
    public boolean isSameContact (String uuid) { return hasContact() && contact.equals(uuid); }

    @ECSearchable(filter=true) @ECField(index=40)
    @ECIndex @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String name;

    @ECSearchable(filter=true) @ECField(index=50)
    @Size(max=100, message="err.remoteHost.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100+ENC_PAD)+")")
    @Getter @Setter private String remoteHost;

    @ECSearchable @ECField(index=60)
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(length=20, nullable=false, updatable=false)
    @Getter @Setter private AccountMessageType messageType;

    @ECSearchable @ECField(index=70)
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(length=20, nullable=false, updatable=false)
    @Getter @Setter private AccountAction action;

    @ECSearchable @ECField(index=80)
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(length=20, nullable=false, updatable=false)
    @Getter @Setter private ActionTarget target;

    @Size(max=100000, message="err.data.length") @ECField(index=90)
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @Getter @Setter private String data;

    @Transient @Getter @Setter private transient AccountMessage request;
    @Transient @Getter @Setter private transient AccountContact requestContact;

    public String templateName(String basename) { return getMessageType()+"/"+ getAction()+"/"+getTarget()+"/"+basename+".hbs"; }

    public long tokenTimeoutSeconds(AccountPolicy policy) {
        if (getMessageType() != AccountMessageType.request) return -1;
        switch (getTarget()) {
            case account: return policy.getAccountOperationTimeout()/1000;
            case network: return policy.getNodeOperationTimeout()/1000;
            default:
                log.warn("tokenTimeout: invalid target: "+getTarget());
                return -1;
        }
    }
}
