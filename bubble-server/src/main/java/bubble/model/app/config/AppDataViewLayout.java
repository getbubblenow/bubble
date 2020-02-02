package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppDataViewLayout {

    table, tiles;

    @JsonCreator public static AppDataViewLayout fromString (String v) { return enumFromString(AppDataViewLayout.class, v); }

}
