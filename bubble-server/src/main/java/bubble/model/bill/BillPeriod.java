package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BillPeriod {

    monthly;

    @JsonCreator public static BillPeriod fromString (String v) { return enumFromString(BillPeriod.class, v); }

}
