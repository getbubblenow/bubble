package bubble.model.bill;

import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.app.BubbleApp;
import bubble.model.cloud.BubbleNetwork;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.List;

import static bubble.ApiConstants.EP_PLANS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_PLANS, listFields={"name", "domain", "description", "account", "enabled"})
@ECTypeChildren(uriPrefix=EP_PLANS+"/{BubblePlan.name}", value={
        @ECTypeChild(type=BubbleNetwork.class, backref="plan"),
        @ECTypeChild(type=BubblePlanApp.class, backref="plan")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class BubblePlan extends IdentifiableBaseParentEntity implements HasAccount, HasPriority {

    public static final int MAX_CHARGENAME_LEN = 12;

    public static final String[] UPDATE_FIELDS = {
            "enabled", "chargeName", "priority", "price", "maxAccounts",
            "nodesIncluded", "storageGbIncluded", "bandwidthGbIncluded",
            "additionalPerNodePrice", "additionalStoragePerGbPrice", "additionalBandwidthPerGbPrice"
    };

    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS,
            "name", "period", "computeSizeType"
    );

    public BubblePlan (BubblePlan other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true) @ECField(index=30, type=EntityFieldType.opaque_string)
    @HasValue(message="err.chargeName.required")
    @Size(max=MAX_CHARGENAME_LEN, message="err.chargeName.length")
    @Column(nullable=false, updatable=false, length=MAX_CHARGENAME_LEN)
    @Getter @Setter private String chargeName;

    public static final DateTimeFormatter MONTHLY_CHARGE_FORMAT = DateTimeFormat.forPattern("MMM-yyyy");

    public String chargeDescription() { return getChargeName()+" "+chargePeriod(period, now()); }

    private String chargePeriod(BillPeriod period, long time) {
        switch (period) {
            case monthly: return MONTHLY_CHARGE_FORMAT.print(time);
            default: return die("chargePeriod: invalid period: "+period);
        }
    }

    @ECSearchable @ECField(index=40)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable @ECField(index=50) @Column(nullable=false)
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Long price;

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false, length=10)
    @Getter @Setter private String currency = "USD";

    @ECSearchable @ECField(index=80)
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private BillPeriod period = BillPeriod.monthly;

    @ECSearchable @ECField(index=90)
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private ComputeNodeSizeType computeSizeType;

    @ECSearchable @ECField(index=100)
    @Getter @Setter private Integer maxAccounts;
    public boolean hasMaxAccounts () { return maxAccounts != null; }
    public int maxAccounts () { return hasMaxAccounts() ? getMaxAccounts() : Integer.MAX_VALUE; }

    @ECSearchable @ECField(index=110)
    @Column(nullable=false)
    @Getter @Setter private Integer nodesIncluded;

    @ECSearchable @ECField(index=120)
    @Column(nullable=false)
    @Getter @Setter private Integer additionalPerNodePrice;

    @ECSearchable @ECField(index=130)
    @Column(nullable=false)
    @Getter @Setter private Integer storageGbIncluded;

    @ECSearchable @ECField(index=140)
    @Column(nullable=false)
    @Getter @Setter private Integer additionalStoragePerGbPrice;

    @ECSearchable @ECField(index=150)
    @Column(nullable=false)
    @Getter @Setter private Integer bandwidthGbIncluded;

    @ECSearchable @ECField(index=160)
    @Column(nullable=false)
    @Getter @Setter private Integer additionalBandwidthPerGbPrice;

    @Transient @Getter @Setter private transient List<BubbleApp> apps;

}
