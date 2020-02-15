package bubble.test.payment;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class PaymentTest extends PaymentTestBase {

    @Test public void testFreePayment () throws Exception { modelTest("payment/pay_free"); }
    @Test public void testCodePayment () throws Exception { modelTest("payment/pay_code"); }
    @Test public void testCreditPayment () throws Exception { modelTest("payment/pay_credit"); }

    @Test public void testCreditPaymentWithRefundAndRestart() throws Exception {
        modelTest("payment/pay_credit_refund_and_restart");
    }

    @Test public void testAppsForPlan () throws Exception { modelTest("payment/plan_apps"); }

}
