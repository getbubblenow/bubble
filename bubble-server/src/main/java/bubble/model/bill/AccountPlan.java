package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.model.bill.BillPeriod.BILL_START_END_FORMAT;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"account", "plan", "network", "name"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"account", "network"}),
        @ECIndex(unique=true, of={"plan", "network"})
})
public class AccountPlan extends IdentifiableBase implements HasAccount {

    public static final String[] UPDATE_FIELDS = {"description", "paymentMethod", "paymentMethodObject"};

    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS,
            "name", "locale", "timezone", "domain", "network", "sshKey", "plan", "footprint");

    @SuppressWarnings("unused")
    public AccountPlan (AccountPlan other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public void beforeCreate() { if (!hasUuid()) initUuid(); }

    // mirrors network name
    @ECSearchable(filter=true) @ECField(index=10)
    @Size(max=100, message="err.name.length")
    @Column(length=100, nullable=false)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECSearchable @ECField(index=50)
    @ECForeignKey(entity=BubbleDomain.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;

    @ECSearchable @ECField(index=60)
    @ECForeignKey(entity=BubbleNetwork.class, index=false) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @ECSearchable @ECField(index=70)
    @ECForeignKey(entity=AccountSshKey.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String sshKey;
    public boolean hasSshKey () { return !empty(sshKey); }

    @ECSearchable @ECField(index=80)
    @Column(nullable=false)
    @Getter @Setter private Boolean enabled = false;
    public boolean enabled() { return enabled != null && enabled; }
    public boolean disabled() { return !enabled(); }

    @ECSearchable(type=EntityFieldType.epoch_time) @ECField(index=90)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Long nextBill;

    @ECSearchable @ECField(index=100)
    @Column(nullable=false, length=20)
    @Getter @Setter private String nextBillDate;
    public AccountPlan setNextBillDate() { return setNextBillDate(BILL_START_END_FORMAT.print(getNextBill())); }

    @ECSearchable @ECField(index=110)
    @ECIndex @Getter @Setter private Long deleted;
    public boolean deleted() { return deleted != null; }
    public boolean notDeleted() { return !deleted(); }

    @ECSearchable @ECField(index=120)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Boolean closed = false;
    public boolean closed() { return closed != null && closed; }
    public boolean notClosed() { return !closed(); }

    @ECSearchable @ECField(index=130)
    @ECIndex(unique=true) @Column(length=UUID_MAXLEN)
    @Getter @Setter private String deletedNetwork;
    public boolean hasDeletedNetwork() { return deletedNetwork != null; }

    // Fields below are used when creating a new plan, to also create the network associated with it
    @Size(max=10000, message="err.description.length")
    @Transient @Getter @Setter private transient String description;

    @Transient @Getter @Setter private transient String locale = null;
    public boolean hasLocale () { return !empty(locale); }

    @Transient @Getter @Setter private transient String timezone = null;
    public boolean hasTimezone () { return !empty(timezone); }

    @Transient @Getter @Setter private transient String footprint = null;
    public boolean hasFootprint () { return !empty(footprint); }

    @Transient @Getter @Setter private transient AccountPaymentMethod paymentMethodObject = null;
    public boolean hasPaymentMethodObject () { return paymentMethodObject != null; }

    public BubbleNetwork bubbleNetwork(Account account,
                                       BubbleDomain domain,
                                       BubblePlan plan,
                                       CloudService storage) {
        return new BubbleNetwork()
                .setName(getName())
                .setDescription(getDescription())
                .setLocale(getLocale())
                .setTimezone(getTimezone())
                .setAccount(account.getUuid())
                .setSshKey(getSshKey())
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setFootprint(getFootprint())
                .setComputeSizeType(plan.getComputeSizeType())
                .setStorage(storage.getUuid());
    }

}
