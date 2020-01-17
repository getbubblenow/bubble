package bubble.service.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum LocalNotificationStrategy {

    http, queue, inline;

    @JsonCreator public LocalNotificationStrategy fromString (String v) { return enumFromString(LocalNotificationStrategy.class, v); }

}
