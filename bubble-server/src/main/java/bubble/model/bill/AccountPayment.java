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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeURIs(listFields={"account", "paymentMethod", "amount"})
@ECTypeFields(list={"account", "paymentMethod", "amount"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AccountPayment extends IdentifiableBase implements HasAccountNoName {

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private AccountPaymentStatus status;

    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(200+ENC_PAD)+")")
    @Getter @Setter private String violation;

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

    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long amount = 0L;

    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String info;

}
