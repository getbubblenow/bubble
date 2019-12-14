package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountPaymentType {

    payment, refund;

    @JsonCreator public static AccountPaymentType fromString (String v) { return enumFromString(AccountPaymentType.class, v); }

}
