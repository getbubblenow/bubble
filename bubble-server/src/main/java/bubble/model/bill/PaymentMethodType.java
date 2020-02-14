package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum PaymentMethodType {

    credit, code, free, promotional_credit;

    public boolean requiresClaim() { return this == code; }
    public boolean requiresAuth() { return this == credit; }

    @JsonCreator public static PaymentMethodType fromString(String v) { return enumFromString(PaymentMethodType.class, v); }

    public boolean refundable() { return this == credit; }

}
