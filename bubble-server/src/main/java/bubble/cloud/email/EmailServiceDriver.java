/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.email;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;

public interface EmailServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.email; }

}
