package bubble.test;

import bubble.server.BubbleConfiguration;
import bubble.service.bill.BillingService;
import bubble.service.bill.StandardRefundService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

@Slf4j
public class PaymentTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-payment"; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();
        configuration.setSpringContextPath("classpath:/spring-mock-network.xml");
        configuration.getStaticAssets().setLocalOverride(null);
        super.beforeStart(server);
    }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();
        configuration.getBean(StandardRefundService.class).start(); // ensure RefundService is always started
        configuration.getBean(BillingService.class).start(); // ensure BillingService is always started
        super.onStart(server);
    }

    @Test public void testFreePayment () throws Exception { modelTest("payment/pay_free"); }
    @Test public void testCodePayment () throws Exception { modelTest("payment/pay_code"); }
    @Test public void testCreditPayment () throws Exception { modelTest("payment/pay_credit"); }

    @Test public void testCreditPaymentWithRefundAndRestart() throws Exception {
        modelTest("payment/pay_credit_refund_and_restart");
    }

    @Test public void testRecurringBilling () throws Exception { modelTest("payment/recurring_billing"); }

}
