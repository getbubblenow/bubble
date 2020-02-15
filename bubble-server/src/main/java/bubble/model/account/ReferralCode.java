package bubble.model.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@ECType(root=true) @ECTypeUpdate(method="DISABLED")
@Entity @NoArgsConstructor @Accessors(chain=true)
public class ReferralCode extends IdentifiableBase implements HasAccount {

    public ReferralCode (ReferralCode other) {
        // only the count is initialized, everything else is set manually
        setCount(other.getCount());
    }

    // update is a noop, must update fields manually
    @Override public Identifiable update(Identifiable thing) { return this; }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String accountUuid;

    @ECSearchable @ECField(index=20)
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private String name;
    public ReferralCode setName () { return setName(randomAlphanumeric(8)); }

    @ECSearchable @ECField(index=30)
    @ECForeignKey(index=false, entity=Account.class) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String claimedBy;

    @ECSearchable @ECField(index=40) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String claimedByUuid;
    public boolean claimed() { return !empty(claimedByUuid); }

    @Transient @Getter @Setter private transient int count = 1;

}
