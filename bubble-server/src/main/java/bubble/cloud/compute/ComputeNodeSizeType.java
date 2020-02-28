/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.compute;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum ComputeNodeSizeType {

    local (0), small (10), medium (25), large (50), xlarge (125);

    @Getter private int costUnits;

    @JsonCreator public static ComputeNodeSizeType fromString (String v) { return enumFromString(ComputeNodeSizeType.class, v); }

}
