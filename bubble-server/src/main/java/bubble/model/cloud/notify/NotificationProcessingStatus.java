package bubble.model.cloud.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum NotificationProcessingStatus {

    received, processing, completed, error;

    @JsonCreator public static NotificationProcessingStatus fromString (String v) { return enumFromString(NotificationProcessingStatus.class, v); }

}
