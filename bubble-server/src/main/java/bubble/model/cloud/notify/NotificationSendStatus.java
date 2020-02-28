/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.cloud.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum NotificationSendStatus {

    created, sending, sent, error;

    @JsonCreator public static NotificationSendStatus fromString(String v) { return enumFromString(NotificationSendStatus.class, v); }

}
