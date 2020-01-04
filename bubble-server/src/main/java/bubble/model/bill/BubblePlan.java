package bubble.model.bill;

import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleNetwork;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_PLANS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_PLANS, listFields={"name", "domain", "description", "account", "enabled"})
@ECTypeChildren(uriPrefix=EP_PLANS+"/{BubblePlan.name}", value={
        @ECTypeChild(type=BubbleNetwork.class, backref="plan")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"account", "name"}) })
public class BubblePlan extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = {
            "name", "chargeName", "enabled", "price", "period", "computeSizeType",
            "nodesIncluded", "additionalPerNodePrice",
            "storageGbIncluded", "additionalStoragePerGbPrice",
            "bandwidthGbIncluded", "additionalBandwidthPerGbPrice"
    };

    public BubblePlan (BubblePlan other) { copy(this, other, CREATE_FIELDS); }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable(filter=true)
    @HasValue(message="err.chargeName.required")
    @Size(message="err.chargeName.length")
    @Column(nullable=false, updatable=false, length=12)
    @Getter @Setter private String chargeName;

    public static final DateTimeFormatter MONTHLY_CHARGE_FORMAT = DateTimeFormat.forPattern("MMM-yyyy");

    public String chargeDescription() { return getChargeName()+" "+chargePeriod(period, now()); }

    private String chargePeriod(BillPeriod period, long time) {
        switch (period) {
            case monthly: return MONTHLY_CHARGE_FORMAT.print(time);
            default: return die("chargePeriod: invalid period: "+period);
        }
    }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Long price;

    @ECSearchable
    @ECIndex @Column(nullable=false, length=10)
    @Getter @Setter private String currency = "USD";

    @ECSearchable
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private BillPeriod period = BillPeriod.monthly;

    @ECSearchable
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private ComputeNodeSizeType computeSizeType;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer nodesIncluded;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer additionalPerNodePrice;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer storageGbIncluded;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer additionalStoragePerGbPrice;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer bandwidthGbIncluded;

    @ECSearchable
    @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer additionalBandwidthPerGbPrice;

}
