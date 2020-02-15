package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class ReferralMonthFreePromotionTest extends PaymentTestBase {

    @Override protected String getManifest() { return "manifest-referral-promo"; }

    @Test public void testReferralMonthFree () throws Exception { modelTest("promo/referral_month_free"); }

}
