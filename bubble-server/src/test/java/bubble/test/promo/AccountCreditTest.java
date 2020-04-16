/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.promo;

import bubble.test.payment.PaymentTestBase;
import org.junit.Test;

public class AccountCreditTest extends PaymentTestBase {

    @Override protected String getManifest() { return "promo/credit/manifest_credit"; }

    @Test public void testAccountCredit () throws Exception { modelTest("promo/account_credit"); }

}
