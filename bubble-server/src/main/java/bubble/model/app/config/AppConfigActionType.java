package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppConfigActionType {

    create, update;

    @JsonCreator public static AppConfigActionType fromString(String v) { return enumFromString(AppConfigActionType.class, v); }

}
