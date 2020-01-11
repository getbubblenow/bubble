package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BubbleNetworkState {

    created, starting, restoring, running, stopping, error_stopping, stopped;

    @JsonCreator public static BubbleNetworkState fromString(String v) { return enumFromString(BubbleNetworkState.class, v); }

    public boolean canStartNetwork () { return this == created || this == stopped; }

}
