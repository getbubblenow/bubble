/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.sms;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;

public interface SmsServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.sms; }

}
