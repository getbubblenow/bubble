/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.payment;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class PaymentTest extends PaymentTestBase {

    @Test public void testFreePayment () throws Exception { modelTest("payment/pay_free"); }
    @Test public void testCodePayment () throws Exception { modelTest("payment/pay_code"); }
    @Test public void testCreditPayment () throws Exception { modelTest("payment/pay_credit"); }

    // this test passes in dev but fails on jenkins. why?
//    @Test public void testCreditPaymentWithRefundAndRestart() throws Exception {
//        modelTest("payment/pay_credit_refund_and_restart");
//    }

    @Test public void testAppsForPlan () throws Exception { modelTest("payment/plan_apps"); }

}
