/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.cloud.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum NotificationProcessingStatus {

    received, processing, completed, error;

    @JsonCreator public static NotificationProcessingStatus fromString (String v) { return enumFromString(NotificationProcessingStatus.class, v); }

}
