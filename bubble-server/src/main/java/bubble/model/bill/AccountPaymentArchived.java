/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.List;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"accountUuid", "ctime"})
@Entity @Accessors(chain=true) @NoArgsConstructor
public class AccountPaymentArchived extends IdentifiableBase {

    @ECSearchable @ECField(index=10, type=EntityFieldType.opaque_string)
    @ECIndex(unique=true) @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String accountUuid;

    @ECSearchable @ECField(index=20, type=EntityFieldType.opaque_string)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, nullable=false, columnDefinition="varchar") // no length limit
    @JsonIgnore @Getter @Setter private String billsJson;

    @Transient public Bill[] getBills() { return json(billsJson, Bill[].class); }
    public AccountPaymentArchived setBills(List<Bill> bills) { return setBillsJson(json(bills, DB_JSON_MAPPER)); }

    @ECSearchable @ECField(index=30)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, nullable=false, columnDefinition="varchar") // no length limit
    @JsonIgnore @Getter @Setter private String paymentsJson;

    @Transient public AccountPayment[] getPayments() { return json(paymentsJson, AccountPayment[].class); }
    public AccountPaymentArchived setPayments(List<AccountPayment> payments) {
        return setPaymentsJson(json(payments, DB_JSON_MAPPER));
    }

    @ECSearchable @ECField(index=40)
    @Type(type=ENCRYPTED_STRING)
    @Column(updatable=false, nullable=false, columnDefinition="varchar") // no length limit
    @JsonIgnore @Getter @Setter private String paymentMethodsJson;

    @Transient public AccountPaymentMethod[] getPaymentMethods() {
        return json(paymentMethodsJson, AccountPaymentMethod[].class);
    }
    public AccountPaymentArchived setPaymentMethods(List<AccountPaymentMethod> paymentMethods) {
        return setPaymentMethodsJson(json(paymentMethods, DB_JSON_MAPPER));
    }
}
