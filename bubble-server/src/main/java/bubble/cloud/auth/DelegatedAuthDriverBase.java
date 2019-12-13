package bubble.cloud.auth;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.model.cloud.CloudService;
import bubble.notify.auth.AuthDriverNotification;

public abstract class DelegatedAuthDriverBase extends DelegatedCloudServiceDriverBase implements AuthenticationDriver {

    public DelegatedAuthDriverBase(CloudService cloud) { super(cloud); }

    protected AuthDriverNotification notification(AuthDriverNotification n) { return n.setAuthService(cloud.getDelegated()); }

}
