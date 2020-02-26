/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.payment;

import org.junit.Test;

public class RecurringBillingTest extends PaymentTestBase {

    @Test public void testRecurringBilling () throws Exception { modelTest("payment/recurring_billing"); }

}
