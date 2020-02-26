/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app.config;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AppDataViewLayout {

    table, tiles;

    @JsonCreator public static AppDataViewLayout fromString (String v) { return enumFromString(AppDataViewLayout.class, v); }

}
