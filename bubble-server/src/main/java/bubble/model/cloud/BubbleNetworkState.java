package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BubbleNetworkState {

    created, setup, starting, restoring, running, stopping, stopped;

    @JsonCreator public static BubbleNetworkState fromString(String v) { return enumFromString(BubbleNetworkState.class, v); }

}
