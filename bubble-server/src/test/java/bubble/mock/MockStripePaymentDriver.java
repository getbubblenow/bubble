package bubble.mock;

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

    @Override public boolean authorize(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        final String err = error.get();
        if (err != null && (err.equals("authorize") || err.equals("all"))) {
            throw invalidEx("err.purchase.authNotFound", "mock: error flag="+err);
        } else {
            return super.authorize(plan, accountPlanUuid, paymentMethod);
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

    @Override protected String charge(BubblePlan plan, AccountPlan accountPlan, AccountPaymentMethod paymentMethod, Bill bill) {
        final String err = error.get();
        if (err != null && (err.equals("charge") || err.equals("all"))) {
            throw invalidEx("err.purchase.declined", "mock: error flag="+err);
        } else {
            return super.charge(plan, accountPlan, paymentMethod, bill);
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
