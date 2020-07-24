/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.bill;

import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.model.bill.AccountPlan;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@Service @Slf4j
public class FirstBillNoticeService extends SimpleDaemon {

    private static final long BILLING_CHECK_INTERVAL = HOURS.toMillis(6);
    @Override protected long getSleepTime() { return BILLING_CHECK_INTERVAL; }

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BillDAO billDAO;

    @Override protected void process() {

        // sort plans by Account ctime, newer Accounts are billed before older Accounts
        final List<AccountPlan> plansToBillSoon = accountPlanDAO.findBillableAccountPlans(now()+DAYS.toMillis(3));

        // iterate plans, find plans that have no promotions to apply and will thus actually be charged
        // for each such plan, send an email to the account owner telling them:
        // when they will be billed, what the amount will be, how it will appear on their statement, and how to cancel

    }
}
