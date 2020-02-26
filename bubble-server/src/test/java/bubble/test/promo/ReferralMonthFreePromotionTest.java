/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class ReferralMonthFreePromotionTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/referral/manifest_referral"; }

    @Test public void testReferralMonthFree () throws Exception { modelTest("promo/referral_month_free"); }

}
