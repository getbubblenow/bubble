package bubble.cloud.sms;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;

public interface SmsServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.sms; }

}
