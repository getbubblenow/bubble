package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum PaymentMethodType {

    credit, paypal, code, free;

    public boolean requiresClaim() { return this == code; }

    @JsonCreator public static PaymentMethodType fromString(String v) { return enumFromString(PaymentMethodType.class, v); }

}
