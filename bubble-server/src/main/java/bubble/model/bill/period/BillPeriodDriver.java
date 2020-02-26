/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.bill.period;

import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import org.joda.time.DurationFieldType;

public interface BillPeriodDriver {

    long calculateRefund(Bill bill, AccountPlan plan);

    DurationFieldType getDurationFieldType();

}
