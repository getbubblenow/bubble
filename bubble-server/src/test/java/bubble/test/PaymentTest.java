package bubble.test;

import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.PhantomUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.system.Sleep.sleep;

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

    @Test public void testFreePayment () throws Exception { modelTest("payment/pay_free"); }
    @Test public void testCodePayment () throws Exception { modelTest("payment/pay_code"); }
    @Test public void testCreditPayment () throws Exception { modelTest("payment/pay_credit"); }

    @Test public void testPhantom () throws Exception {
        log.info("go for it!");
        sleep(TimeUnit.MINUTES.toMillis(60));
        PhantomUtil.init();
        // new PhantomUtil().loadPageAndExec(getConfiguration().getPublicUriBase()+"/stripe/index.html", "");
    }

}
