package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class FirstMonthFreePromotionTest extends PaymentTestBase {

    @Override protected String getManifest() { return "manifest-1mo-promo"; }

    @Test public void testFirstMonthFree () throws Exception {
        modelTest("promo/first_month_free");
    }

}
