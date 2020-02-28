/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.app;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppDataFormat {

    key, value, key_value, full;

    @JsonCreator public static AppDataFormat fromString (String v) { return enumFromString(AppDataFormat.class, v); }

}
