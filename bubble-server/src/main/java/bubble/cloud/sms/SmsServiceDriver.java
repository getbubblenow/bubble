/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.sms;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;

public interface SmsServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.sms; }

}
