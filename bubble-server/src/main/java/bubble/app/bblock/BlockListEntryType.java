/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.app.bblock;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BlockListEntryType {

    builtin, custom;

    @JsonCreator public static BlockListEntryType fromString (String v) { return enumFromString(BlockListEntryType.class, v); }

}
