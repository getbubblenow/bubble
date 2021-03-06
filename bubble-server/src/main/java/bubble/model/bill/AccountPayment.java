/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
import org.cobbzilla.wizard.validation.MultiViolationException;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"account", "paymentMethod", "amount"})
@ECIndexes({
        @ECIndex(name="account_payment_uniq_bill_success_payment",
                 unique=true,
                 of={"bill", "type"},
                 where="status = 'success' AND type = 'payment'")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AccountPayment extends IdentifiableBase implements HasAccountNoName {

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountPlan;

    @ECSearchable @ECField(index=50)
    @ECForeignKey(entity=Bill.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String bill;

    @ECIndex @ECSearchable @ECField(index=60)
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private AccountPaymentType type;

    @ECIndex @ECSearchable @ECField(index=70)
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private AccountPaymentStatus status;

    @ECIndex @ECSearchable @ECField(index=80)
    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long amount = 0L;

    @JsonIgnore @Transient public int getAmountInt() { return (int) (amount == null ? 0 : amount); }

    @ECSearchable @ECField(index=90)
    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @ECSearchable(filter=true) @ECField(index=100, type=EntityFieldType.opaque_string)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String info;

    @ECSearchable @ECField(index=110, type=EntityFieldType.error)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(200+ENC_PAD)+")")
    @Getter @Setter private String violation;

    @ECSearchable @ECField(index=120, type=EntityFieldType.error)
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

    @Transient @Getter @Setter private transient Bill billObject;
    @Transient @Getter @Setter private transient AccountPaymentMethod paymentMethodObject;

    public static int totalPayments (List<AccountPayment> payments) {
        return empty(payments) ? 0 : payments.stream().mapToInt(AccountPayment::getAmountInt).sum();
    }
}
