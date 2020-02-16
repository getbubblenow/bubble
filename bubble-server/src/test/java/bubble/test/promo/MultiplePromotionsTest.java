package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class MultiplePromotionsTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/multi/manifest_multi"; }

    @Test public void testMultiplePromotions() throws Exception { modelTest("promo/multi_promo"); }

}
