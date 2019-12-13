package bubble.model.cloud.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum NotificationSendStatus {

    created, sending, sent, error;

    @JsonCreator public static NotificationSendStatus fromString(String v) { return enumFromString(NotificationSendStatus.class, v); }

}
