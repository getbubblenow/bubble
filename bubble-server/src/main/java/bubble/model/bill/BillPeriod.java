package bubble.model.bill;

import bubble.model.bill.period.BillPeriodDriver;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM;

@AllArgsConstructor
public enum BillPeriod {

    monthly (DATE_FORMAT_YYYY_MM);

    @Getter private DateTimeFormatter formatter;
    @Getter(lazy=true) private final BillPeriodDriver driver = instantiate(getClass().getName()+"_"+name());

    @JsonCreator public static BillPeriod fromString (String v) { return enumFromString(BillPeriod.class, v); }

    public String currentPeriod() { return formatter.print(now()); }

    public long calculateRefund(long planStart, String period, long total) {
        return getDriver().calculateRefund(planStart, period, total);
    }

    public DateTime nextPeriod() { return getDriver().nextPeriod(); }

}
