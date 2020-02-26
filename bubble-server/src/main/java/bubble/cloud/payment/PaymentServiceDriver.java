/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.payment;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public interface PaymentServiceDriver extends CloudServiceDriver {

    default CloudServiceType getType() { return CloudServiceType.payment; }

    PaymentMethodType getPaymentMethodType();

    PaymentValidationResult validate(AccountPaymentMethod paymentMethod);

    default PaymentValidationResult claim(AccountPaymentMethod paymentMethod) { return notSupported("claim"); }
    default PaymentValidationResult claim(AccountPlan accountPlan) { return notSupported("claim"); }

    boolean authorize(BubblePlan plan, String accountPlanUuid, String billUuid, AccountPaymentMethod paymentMethod);

    boolean cancelAuthorization(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod);

    boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid);

    boolean refund(String accountPlanUuid);

    @Override default boolean test () { return true; }

}
