package bubble.model.bill.period;

import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import org.joda.time.DurationFieldType;

public interface BillPeriodDriver {

    long calculateRefund(Bill bill, AccountPlan plan);

    DurationFieldType getDurationFieldType();

}
