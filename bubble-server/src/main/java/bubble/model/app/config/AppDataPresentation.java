package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppDataPresentation {

    none, app, site, app_and_site;

    @JsonCreator public static AppDataPresentation fromString (String v) { return enumFromString(AppDataPresentation.class, v); }

}
