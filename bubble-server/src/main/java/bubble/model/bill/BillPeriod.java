package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import org.joda.time.format.DateTimeFormatter;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM;

@AllArgsConstructor
public enum BillPeriod {

    monthly (DATE_FORMAT_YYYY_MM);

    private DateTimeFormatter formatter;

    @JsonCreator public static BillPeriod fromString (String v) { return enumFromString(BillPeriod.class, v); }

    public String currentPeriod() { return formatter.print(now()); }

}
