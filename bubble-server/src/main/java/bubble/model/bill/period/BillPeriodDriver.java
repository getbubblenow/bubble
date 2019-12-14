package bubble.model.bill.period;

import org.joda.time.DateTime;

public interface BillPeriodDriver {

    long calculateRefund(long planStart, String period, long total);

    DateTime nextPeriod();

}
