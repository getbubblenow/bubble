/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.payment.stripe.mock;

import bubble.cloud.payment.ChargeResult;
import bubble.cloud.payment.stripe.StripePaymentDriver;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.bill.BubblePlan;
import com.stripe.Stripe;

import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public class MockStripePaymentDriver extends StripePaymentDriver {

    public static final AtomicReference<String> error = new AtomicReference<>(null);
    public static void setError(String err) { error.set(err); }

    @Override public void postSetup() {
        Stripe.apiKey = getCredentials().getParam(PARAM_SECRET_API_KEY);;
    }

    @Override public boolean authorize(BubblePlan plan, String accountPlanUuid, String billUuid, AccountPaymentMethod paymentMethod) {
        final String err = error.get();
        if (err != null && (err.equals("authorize") || err.equals("all"))) {
            throw invalidEx("err.purchase.authNotFound", "mock: error flag="+err);
        } else {
            return super.authorize(plan, accountPlanUuid, billUuid, paymentMethod);
        }
    }

    @Override public boolean cancelAuthorization(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        final String err = error.get();
        if (err != null && (err.equals("cancelAuthorization") || err.equals("all"))) {
            throw invalidEx("err.purchase.authNotFound", "mock: error flag="+err);
        } else {
            return super.cancelAuthorization(plan, accountPlanUuid, paymentMethod);
        }
    }

    @Override protected ChargeResult charge(BubblePlan plan, AccountPlan accountPlan, AccountPaymentMethod paymentMethod, Bill bill, long chargeAmount) {
        final String err = error.get();
        if (err != null && (err.equals("charge") || err.equals("all"))) {
            throw invalidEx("err.purchase.declined", "mock: error flag="+err);
        } else {
            return super.charge(plan, accountPlan, paymentMethod, bill, chargeAmount);
        }
    }

    @Override public boolean refund(String accountPlanUuid) {
        final String err = error.get();
        if (err != null && (err.equals("refund") || err.equals("all"))) {
            throw invalidEx("err.refund.unknownError", "mock: error flag="+err);
        } else {
            return super.refund(accountPlanUuid);
        }
    }

}
