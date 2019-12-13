package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BillItemType {

    compute, storage, bandwidth;

    @JsonCreator public static BillItemType fromString(String v) { return enumFromString(BillItemType.class, v); }

}
