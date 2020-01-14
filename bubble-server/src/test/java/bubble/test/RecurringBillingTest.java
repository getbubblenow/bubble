package bubble.test;

import org.junit.Test;

public class RecurringBillingTest extends PaymentTestBase {

    @Test public void testRecurringBilling () throws Exception { modelTest("payment/recurring_billing"); }

}
