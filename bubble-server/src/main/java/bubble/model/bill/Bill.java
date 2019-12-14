package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeURIs(listFields={"name", "status", "type", "quantity", "price", "period"})
@ECTypeFields(list={"name", "Status", "type", "quantity", "price", "period"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "plan", "type", "period"})
})
public class Bill extends IdentifiableBase implements HasAccountNoName {

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @ECIndex @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    @Getter @Setter private BillStatus status = BillStatus.unpaid;
    public boolean paid() { return status == BillStatus.paid; }
    public boolean unpaid() { return !paid(); }

    @ECForeignKey(entity=AccountPayment.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String payment;
    public boolean hasPayment () { return payment != null; }

    @ECIndex @Enumerated(EnumType.STRING)
    @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private BillItemType type;

    @Column(nullable=false, updatable=false, length=50)
    @ECIndex @Getter @Setter private String period;

    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long quantity = 0L;

    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long price = 0L;

    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @Type(type=ENCRYPTED_LONG) @Column(columnDefinition="varchar("+(ENC_LONG)+")")
    @Getter @Setter private Long refundedAmount = 0L;
    public boolean hasRefundedAmount () { return refundedAmount != null && refundedAmount > 0L; }

    @JsonIgnore @Transient public long getTotal() { return big(quantity).multiply(big(price)).longValue(); }

}
