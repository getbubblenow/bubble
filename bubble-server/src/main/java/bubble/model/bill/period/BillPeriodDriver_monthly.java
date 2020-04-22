/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.bill.period;

import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.DurationFieldType;

import java.math.RoundingMode;

import static bubble.model.bill.BillPeriod.BILL_START_END_FORMAT;
import static org.cobbzilla.util.daemon.ZillaRuntime.big;

@Slf4j
public class BillPeriodDriver_monthly implements BillPeriodDriver {

    @Override public DurationFieldType getDurationFieldType() { return DurationFieldType.months(); }

    @Override public long calculateRefund(Bill bill, AccountPlan plan) {
        final DateTime endTime = new DateTime(plan.getDeleted()).plusDays(1).withTimeAtStartOfDay();
        final DateTime startPeriod = BILL_START_END_FORMAT.parseDateTime(bill.getPeriodStart());
        final DateTime endPeriod = BILL_START_END_FORMAT.parseDateTime(bill.getPeriodEnd());

        final int daysInPeriod = Days.daysBetween(startPeriod, endPeriod).getDays();
        final int daysRemaining = Days.daysBetween(endTime, endPeriod).getDays();
        if (daysRemaining == 0) {
            log.info("calculateRefund: no days remaining in period, no refund to issue");
            return 0L;
        }
        return big(bill.getTotal())
                .multiply(big(daysRemaining))
                .divide(big(daysInPeriod), RoundingMode.HALF_EVEN)
                .longValue();
    }

}
