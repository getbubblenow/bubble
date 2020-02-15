package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class FirstMonthFreePromotionTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/1mo/manifest_1mo"; }

    @Test public void testFirstMonthFree () throws Exception { modelTest("promo/first_month_free"); }

}
