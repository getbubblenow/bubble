package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"name", "status", "type", "quantity", "price", "periodStart"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "accountPlan", "type", "periodStart"})
})
public class Bill extends IdentifiableBase implements HasAccountNoName {

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @ECSearchable @ECField(index=40)
    @ECIndex @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    @Getter @Setter private BillStatus status = BillStatus.unpaid;
    public boolean paid() { return status == BillStatus.paid; }
    public boolean unpaid() { return !paid(); }

    @ECSearchable @ECField(index=50)
    @ECIndex @Enumerated(EnumType.STRING)
    @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private BillItemType type;

    @ECSearchable @ECField(index=60, type=EntityFieldType.opaque_string)
    @Column(nullable=false, updatable=false, length=20)
    @ECIndex @Getter @Setter private String periodLabel;

    @ECSearchable @ECField(index=70, type=EntityFieldType.opaque_string)
    @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private String periodStart;

    @ECSearchable @ECField(index=80, type=EntityFieldType.opaque_string)
    @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private String periodEnd;

    public int daysInPeriod () { return BillPeriod.daysInPeriod(periodStart, periodEnd); }

    @ECSearchable @ECField(index=90)
    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long quantity = 0L;

    @ECSearchable @ECField(index=100)
    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long price = 0L;

    @ECSearchable @ECField(index=110)
    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @ECSearchable @ECField(index=120)
    @Type(type=ENCRYPTED_LONG) @Column(columnDefinition="varchar("+(ENC_LONG)+")")
    @Getter @Setter private Long refundedAmount = 0L;
    public boolean hasRefundedAmount () { return refundedAmount != null && refundedAmount > 0L; }

    @JsonIgnore @Transient public long getTotal() { return big(quantity).multiply(big(price)).longValue(); }

}
