/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.promo;

import bubble.auth.PromoCodePolicy;
import bubble.test.payment.PaymentTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

@Slf4j
public class MultiplePromotionsTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/multi/manifest_multi"; }

    @Before public void ensurePromoCodeOptional() { getConfiguration().setPromoCodePolicy(PromoCodePolicy.optional); }

    @Test public void testMultiplePromotions() throws Exception { modelTest("promo/multi_promo"); }

}
