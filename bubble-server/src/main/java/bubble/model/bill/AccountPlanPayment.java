package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_LONG;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_LONG;

@ECType(root=true) @ECTypeURIs(listFields={"account", "plan", "accountPlan", "payment", "bill"})
@ECTypeFields(list={"account", "plan", "accountPlan", "payment", "bill"})
@ECIndexes({
        @ECIndex(unique=true, of={"planPaymentMethod", "bill"})
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AccountPlanPayment extends IdentifiableBase implements HasAccountNoName {

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @ECForeignKey(entity=AccountPayment.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String payment;

    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECForeignKey(entity=AccountPlanPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String planPaymentMethod;

    @ECIndex(unique=true) @ECForeignKey(index=false, entity=Bill.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String bill;

    @Column(nullable=false, updatable=false, length=50)
    @ECIndex @Getter @Setter private String period;

    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long amount = 0L;

    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @Transient @Getter @Setter private transient AccountPlan accountPlanObject;
    @Transient @Getter @Setter private transient AccountPayment paymentObject;
    @Transient @Getter @Setter private transient Bill billObject;

}
