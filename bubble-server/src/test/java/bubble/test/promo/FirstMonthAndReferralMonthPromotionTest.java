package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class FirstMonthAndReferralMonthPromotionTest extends PaymentTestBase {

    @Test public void testFirstMonthAndMultipleReferralMonthsFree () throws Exception {
        modelTest("promo/first_month_and_multiple_referral_months_free");
    }

}
