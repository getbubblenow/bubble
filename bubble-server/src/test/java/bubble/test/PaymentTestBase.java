package bubble.test;

import bubble.server.BubbleConfiguration;
import bubble.service.bill.BillingService;
import bubble.service.bill.StandardRefundService;
import org.cobbzilla.wizard.server.RestServer;

public class PaymentTestBase extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

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

}
