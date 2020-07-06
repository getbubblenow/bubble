/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum ComputeNodeSizeType {

    local,
    tiny,
    small,
    medium,
    large,
    xlarge;

    @JsonCreator public static ComputeNodeSizeType fromString (String v) { return enumFromString(ComputeNodeSizeType.class, v); }

}
