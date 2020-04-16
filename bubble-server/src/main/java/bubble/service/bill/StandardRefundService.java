/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Service @Slf4j
public class StandardRefundService extends SimpleDaemon implements RefundService {

    private static final long REFUND_CHECK_INTERVAL = HOURS.toMillis(6);

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public void processRefunds () { interrupt(); }

    @Override protected long getSleepTime() { return REFUND_CHECK_INTERVAL; }

    @Override protected boolean canInterruptSleep() { return true; }

    @Override protected void process() {
        // iterate over all account plans that have been deleted but not yet closed
        final List<AccountPlan> pendingPlans = accountPlanDAO.findByDeletedAndNotClosedAndNoRefundIssued();
        for (AccountPlan accountPlan : pendingPlans) {
            try {
                final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());
                if (!paymentMethod.getPaymentMethodType().refundable()) {
                    log.info("process: not issuing refund for plan "+accountPlan.getUuid()+" because paymentMethodType="+paymentMethod.getPaymentMethodType());
                    continue;
                }
                final CloudService paymentCloud = cloudDAO.findByUuid(paymentMethod.getCloud());
                final PaymentServiceDriver paymentDriver = paymentCloud.getPaymentDriver(configuration);
                paymentDriver.refund(accountPlan.getUuid());

            } catch (Exception e) {
                log.error("process: error processing refund for AccountPlan: "+accountPlan.getUuid());
                accountPlan.setRefundError(shortError(e));

            } finally {
                accountPlanDAO.update(accountPlan.setRefundIssued(true));
            }
        }
    }

}
