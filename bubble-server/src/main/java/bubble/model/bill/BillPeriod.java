/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.bill;

import bubble.model.bill.period.BillPeriodDriver;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.DurationFieldType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM;

@AllArgsConstructor
public enum BillPeriod {

    monthly (DATE_FORMAT_YYYY_MM);

    public static final DateTimeFormatter BILL_START_END_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");

    @Getter private DateTimeFormatter formatter;
    @Getter(lazy=true) private final BillPeriodDriver driver = instantiate(BillPeriodDriver.class.getName()+"_"+name());

    @JsonCreator public static BillPeriod fromString (String v) { return enumFromString(BillPeriod.class, v); }

    public static int daysInPeriod(String periodStart, String periodEnd) {
        final DateTime start = new DateTime(BILL_START_END_FORMAT.parseMillis(periodStart));
        final DateTime end = new DateTime(BILL_START_END_FORMAT.parseMillis(periodEnd));
        return Days.daysBetween(start.withTimeAtStartOfDay(), end.withTimeAtStartOfDay()).getDays();
    }

    public long calculateRefund(Bill bill, AccountPlan accountPlan) {
        return getDriver().calculateRefund(bill, accountPlan);
    }

    public String periodLabel(long t) { return getFormatter().print(t); }

    public String periodStart(long time) { return BILL_START_END_FORMAT.print(new DateTime(time).withTimeAtStartOfDay()); }

    public String periodEnd(long time) {
        final DurationFieldType fieldType = getDriver().getDurationFieldType();
        return BILL_START_END_FORMAT.print(new DateTime(time).withTimeAtStartOfDay().withFieldAdded(fieldType, 1));
    }

    public long periodMillis(String period) {
        return new DateTime(BILL_START_END_FORMAT.parseMillis(period)).withTimeAtStartOfDay().getMillis();
    }
}
