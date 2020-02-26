/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.server.listener;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum FirstTimeType {

    install, restore;

    @JsonCreator public static FirstTimeType fromString(String val) { return enumFromString(FirstTimeType.class, val); }

}
