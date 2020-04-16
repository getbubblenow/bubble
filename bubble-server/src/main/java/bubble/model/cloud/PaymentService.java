/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.model.bill.PaymentMethodType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentService extends CloudService {

    public PaymentService (CloudService other, PaymentMethodType type) {
        super(other);
        setPaymentMethodType(type);
    }

    @Transient @Getter @Setter private transient PaymentMethodType paymentMethodType;

    @Override public String getCredentialsJson() { return null; }

}
