/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.ArrayUtils;

import static bubble.ApiConstants.enumFromString;

public enum BubbleNodeState {

    created, starting, booting, booted, preparing_install, installing, running,
    stopping, stopped,
    unreachable, error_stopping, error_stopped, unknown_error;

    public static final BubbleNodeState[] ACTIVE_STATES = {
            created, starting, booting, booted, preparing_install, installing, running, stopping
    };

    @JsonCreator public static BubbleNodeState fromString (String v) { return enumFromString(BubbleNodeState.class, v); }

    public boolean active () { return ArrayUtils.contains(ACTIVE_STATES, this); }

}
