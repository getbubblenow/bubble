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
import org.cobbzilla.wizard.validation.MultiViolationException;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeURIs(listFields={"account", "paymentMethod", "amount"})
@ECTypeFields(list={"account", "paymentMethod", "amount"})
@ECIndexes({
        @ECIndex(name="account_payment_uniq_bill_success", unique=true, of={"bill"}, where="status = 'success'")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AccountPayment extends IdentifiableBase implements HasAccountNoName {

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable
    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECSearchable
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable
    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @ECSearchable
    @ECForeignKey(entity=Bill.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String bill;

    @ECSearchable
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private AccountPaymentType type;

    @ECSearchable
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private AccountPaymentStatus status;

    @ECSearchable
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(200+ENC_PAD)+")")
    @Getter @Setter private String violation;

    @ECSearchable
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String exception;

    public AccountPayment setError (Exception e) {
        if (e instanceof SimpleViolationException) {
            setViolation(((SimpleViolationException) e).getMessageTemplate());
        } else if (e instanceof MultiViolationException) {
            setViolation(((MultiViolationException) e).getViolations().get(0).getMessageTemplate());
        }
        setException(errorString(e));
        return this;
    }

    @ECSearchable
    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long amount = 0L;

    @ECSearchable
    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @ECSearchable(filter=true)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String info;

    @Transient @Getter @Setter private transient Bill billObject;

}
