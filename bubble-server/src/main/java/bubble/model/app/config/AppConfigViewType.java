package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppConfigViewType {

    list, item;

    @JsonCreator public static AppConfigViewType fromString (String v) { return enumFromString(AppConfigViewType.class, v); }

}
