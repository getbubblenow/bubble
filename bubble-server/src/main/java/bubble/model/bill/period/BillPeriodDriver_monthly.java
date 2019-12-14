package bubble.model.bill.period;

import bubble.model.bill.BillPeriod;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import java.math.RoundingMode;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class BillPeriodDriver_monthly implements BillPeriodDriver {

    @Override public long calculateRefund(long planStart, String period, long total) {
        final DateTime currentTime = new DateTime(now()).withTimeAtStartOfDay();
        final DateTime periodStart = BillPeriod.monthly.getFormatter().parseDateTime(period);
        final DateTime nextPeriodStart = nextPeriod();

        if (currentTime.getMillis() < periodStart.getMillis()) {
            // this should never happen
            log.error("calculateRefund: currentTime + 1 day is BEFORE periodStart, invalid");
            throw invalidEx("err.refund.periodError");
        }
        if (currentTime.getMillis() > nextPeriodStart.getMillis()) {
            log.warn("calculateRefund: currentTime + 1 day is after nextPeriodStart, no refund");
            return 0L;
        }

        final int daysInPeriod = Days.daysBetween(periodStart, nextPeriodStart).getDays();
        final int daysUsed = Days.daysBetween(periodStart, currentTime).getDays();
        if (daysUsed == 0) {
            log.warn("calculateRefund: no days used, assuming 1 day used");
            return prorated(total, daysInPeriod - 1, daysInPeriod);
        } else {
            return prorated(total, daysInPeriod - daysUsed, daysInPeriod);
        }
    }

    private long prorated(long total, int daysUsed, int daysInPeriod) {
        return big(total)
                .multiply(big(daysUsed))
                .divide(big(daysInPeriod), RoundingMode.HALF_EVEN).longValue();
    }

    @Override public DateTime nextPeriod() {
        return new LocalDate(now()).plusMonths(1).withDayOfMonth(1).toDateTimeAtStartOfDay();
    }
}
