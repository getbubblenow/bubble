package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
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

@ECType(root=true)
@ECTypeURIs(listFields={"account", "plan", "network", "name"})
@ECTypeFields(list={"account", "plan", "network", "name"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"account", "network"}),
        @ECIndex(unique=true, of={"plan", "network"})
})
public class AccountPlan extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = {
            "name", "description", "locale", "timezone", "domain", "network", "plan", "footprint",
            "paymentMethod", "paymentMethodObject"
    };

    @SuppressWarnings("unused")
    public AccountPlan (AccountPlan other) { copy(this, other, CREATE_FIELDS); }

    @Override public void beforeCreate() { if (!hasUuid()) initUuid(); }

    // mirrors network name
    @ECSearchable(filter=true)
    @Size(max=100, message="err.name.length")
    @Column(length=100, nullable=false)
    @Getter @Setter private String name;

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable
    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECSearchable
    @ECForeignKey(entity=BubbleDomain.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;

    @ECSearchable
    @ECForeignKey(entity=BubbleNetwork.class, index=false) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @ECSearchable
    @Column(nullable=false)
    @Getter @Setter private Boolean enabled = false;
    public boolean enabled() { return enabled != null && enabled; }
    public boolean disabled() { return !enabled(); }

    @ECSearchable(type=EntityFieldType.epoch_time)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Long nextBill;

    @ECSearchable
    @Column(nullable=false, length=20)
    @Getter @Setter private String nextBillDate;
    public AccountPlan setNextBillDate() { return setNextBillDate(BILL_START_END_FORMAT.print(getNextBill())); }

    @ECSearchable
    @ECIndex @Getter @Setter private Long deleted;
    public boolean deleted() { return deleted != null; }
    public boolean notDeleted() { return !deleted(); }

    @ECSearchable
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Boolean closed = false;
    public boolean closed() { return closed != null && closed; }
    public boolean notClosed() { return !closed(); }

    @ECSearchable
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
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setFootprint(getFootprint())
                .setComputeSizeType(plan.getComputeSizeType())
                .setStorage(storage.getUuid());
    }

}
