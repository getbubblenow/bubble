/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum LaunchType {

    node, fork_node, fork_sage;

    @JsonCreator public static LaunchType fromString(String v) { return enumFromString(LaunchType.class, v); }

}
