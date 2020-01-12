package bubble.model.account.message;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum ActionTarget {

    network, account;

    @JsonCreator public static ActionTarget fromString (String v) { return enumFromString(ActionTarget.class, v); }

}
