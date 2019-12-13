package bubble.server.listener;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum FirstTimeType {

    install, restore;

    @JsonCreator public static FirstTimeType fromString(String val) { return enumFromString(FirstTimeType.class, val); }

}
