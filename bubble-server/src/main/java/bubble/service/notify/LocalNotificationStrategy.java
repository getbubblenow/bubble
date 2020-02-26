/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.notify;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum LocalNotificationStrategy {

    http, queue, inline;

    @JsonCreator public LocalNotificationStrategy fromString (String v) { return enumFromString(LocalNotificationStrategy.class, v); }

}
