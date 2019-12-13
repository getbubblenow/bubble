package bubble.cloud.email;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;

public interface EmailServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.email; }

}
