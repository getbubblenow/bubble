/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.bill;

import bubble.model.account.Account;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static bubble.model.bill.Bill.PERIOD_FIELDS_MAX_LENGTH;
import static bubble.model.bill.BubblePlan.PLAN_NAME_MAX_LENGTH;
import static bubble.model.cloud.BubbleNetwork.NETWORK_NAME_MAXLEN;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.NamedEntity.NAME_MAXLEN;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"accountName", "paymentMethodMaskedInfo", "amount"})
@Entity @Accessors(chain=true)
public class AccountPaymentArchived extends IdentifiableBase {

    /**
     * List of properties from AccountPayment class which will not be used in this class the same way. Those foreign
     * keys (uuids) will not be available within referenced tables, and so here the other unique data from those
     * elements is extracted.
     */
    private static final String[] ALTERED_FIELDS = { "account", "paymentMethod", "plan", "accountPlan", "bill" };

    public AccountPaymentArchived(@NonNull final AccountPayment original, @NonNull final String accountName,
                                  @NonNull final String paymentMethodMaskedInfo, @NonNull final String bubblePlanName,
                                  @NonNull final String accountPlanName, @NonNull final String billPeriodStart) {
        this.accountUuid = original.getAccount();
        this.accountName = accountName;
        this.paymentMethodMaskedInfo = paymentMethodMaskedInfo;
        this.bubblePlanName = bubblePlanName;
        this.accountPlanName = accountPlanName;
        this.billPeriodStart = billPeriodStart;

        copy(this, original, null, ALTERED_FIELDS);
    }

    /**
     * Storing deleted account UUID here is required as user may select full deletion of his/her account while not
     * waiving his/her rights to sue. So then these records must stay related to exactly that user which might not be
     * unique by its name in the future.
     */
    @ECSearchable @ECField(index=5)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition="varchar(" + (UUID_MAXLEN + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String accountUuid;

    @ECSearchable @ECField(index=10)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition="varchar(" + (Account.NAME_MAX_LENGTH + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String accountName;

    @ECSearchable @ECField(index=20, type=EntityFieldType.opaque_string)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition="varchar(" + (NAME_MAXLEN + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String paymentMethodMaskedInfo;

    @ECSearchable @ECField(index=30)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition="varchar(" + (PLAN_NAME_MAX_LENGTH + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String bubblePlanName;

    @ECSearchable @ECField(index=40)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition= "varchar(" + (NETWORK_NAME_MAXLEN + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String accountPlanName;

    @ECSearchable @ECField(index=50, type=EntityFieldType.opaque_string)
    @Column(updatable=false, columnDefinition= "varchar(" + (PERIOD_FIELDS_MAX_LENGTH + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String billPeriodStart;

    @ECIndex @ECSearchable @ECField(index=60)
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private AccountPaymentType type;

    @ECIndex @ECSearchable @ECField(index=70)
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private AccountPaymentStatus status;

    @ECIndex @ECSearchable @ECField(index=80)
    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar(" + ENC_LONG + ") NOT NULL")
    @Getter @Setter private Long amount;

    @ECSearchable @ECField(index=90)
    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @ECSearchable(filter=true) @ECField(index=100, type=EntityFieldType.opaque_string)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, columnDefinition="varchar(" + (100_000 + ENC_PAD) + ") NOT NULL")
    @Getter @Setter private String info;

    @ECSearchable @ECField(index=110, type=EntityFieldType.error)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar(" + (200 + ENC_PAD) + ")")
    @Getter @Setter private String violation;

    @ECSearchable @ECField(index=120, type=EntityFieldType.error)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar(" + (10_000 + ENC_PAD) + ")")
    @JsonIgnore @Getter @Setter private String exception;
}
