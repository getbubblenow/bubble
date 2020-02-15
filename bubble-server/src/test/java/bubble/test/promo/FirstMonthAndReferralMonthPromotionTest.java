package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class FirstMonthAndReferralMonthPromotionTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/1mo_and_referral/manifest_1m_and_referral"; }

    @Test public void testFirstMonthAndMultipleReferralMonthsFree () throws Exception {
        modelTest("promo/first_month_and_multiple_referral_months_free");
    }

}
