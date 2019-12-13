package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AuthFactorType {

    not_required, required, sufficient;

    @JsonCreator public static AuthFactorType fromString (String v) { return enumFromString(AuthFactorType.class, v); }

}
