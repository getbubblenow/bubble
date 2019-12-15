package bubble.test;

import bubble.server.BubbleConfiguration;
import bubble.service.bill.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

@Slf4j
public class PaymentTest extends ActivatedBubbleModelTestBase {

    @Override protected String getModelPrefix() { return "models/"; }
    @Override protected String getManifest() { return "manifest-payment"; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();
        configuration.setSpringContextPath("classpath:/spring-mock-network.xml");
        configuration.getStaticAssets().setLocalOverride(null);
        super.beforeStart(server);
    }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();
        configuration.getBean(RefundService.class).start(); // ensure RefundService is always started
        super.onStart(server);
    }

    @Test public void testFreePayment () throws Exception { modelTest("payment/pay_free"); }
    @Test public void testCodePayment () throws Exception { modelTest("payment/pay_code"); }
    @Test public void testCreditPayment () throws Exception { modelTest("payment/pay_credit"); }

    @Test public void testCreditPaymentMultipleStartStop () throws Exception {
        modelTest("payment/pay_credit_multi_start_stop");
    }


}
