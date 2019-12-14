package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BillStatus {

    unpaid, paid;

    @JsonCreator public static BillStatus fromString (String v) { return enumFromString(BillStatus.class, v); }

}
