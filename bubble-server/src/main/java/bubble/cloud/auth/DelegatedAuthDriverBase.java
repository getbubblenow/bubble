/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.auth;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.model.cloud.CloudService;
import bubble.notify.auth.AuthDriverNotification;

public abstract class DelegatedAuthDriverBase extends DelegatedCloudServiceDriverBase implements AuthenticationDriver {

    public DelegatedAuthDriverBase(CloudService cloud) { super(cloud); }

    protected AuthDriverNotification notification(AuthDriverNotification n) { return n.setAuthService(cloud.getDelegated()); }

}
