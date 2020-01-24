package bubble.model.app;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BubbleAppDataPresentation {

    app, site, app_and_site;

    @JsonCreator public static BubbleAppDataPresentation fromString (String v) { return enumFromString(BubbleAppDataPresentation.class, v); }

}
