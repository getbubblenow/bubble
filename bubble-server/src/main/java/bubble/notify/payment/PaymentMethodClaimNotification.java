/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.payment;

import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentMethodClaimNotification extends SynchronousNotification {

    @Getter @Setter private AccountPaymentMethod paymentMethod;
    public boolean hasPaymentMethod () { return paymentMethod != null; }

    @Getter @Setter private AccountPlan accountPlan;
    public boolean hasAccountPlan() { return accountPlan != null; }

    @Getter @Setter private String cloud;

    public PaymentMethodClaimNotification(String cloud, AccountPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.cloud = cloud;
    }

    public PaymentMethodClaimNotification(String cloud, AccountPlan accountPlan) {
        this.accountPlan = accountPlan;
        this.cloud = cloud;
    }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(hasPaymentMethod() ? paymentMethod.getCacheKey() : null, hasAccountPlan() ? accountPlan.getUuid() : null, cloud);
}
