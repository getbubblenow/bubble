/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppConfigScope {

    app, item;

    @JsonCreator public static AppConfigScope fromString(String v) { return enumFromString(AppConfigScope.class, v); }

}
