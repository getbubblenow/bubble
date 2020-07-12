/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BubbleNetworkState {

    created, starting, restoring, running, stopping, error_stopping, stopped;

    @JsonCreator public static BubbleNetworkState fromString(String v) { return enumFromString(BubbleNetworkState.class, v); }

    public boolean canStart() { return this == created || this == stopped; }

    public boolean canStop() { return this != stopped && this != error_stopping; }

    public boolean isStopped() {
         return this == BubbleNetworkState.stopped || this == BubbleNetworkState.error_stopping;
    }

}
