/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum LaunchType {

    node      (false),
    fork_node (true),
    fork_sage (true);

    @JsonCreator public static LaunchType fromString(String v) { return enumFromString(LaunchType.class, v); }

    private final boolean fork;
    public boolean fork () { return fork; }

}
