package bubble.cloud.authenticator;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import com.fasterxml.jackson.databind.JsonNode;

public interface AuthenticatorServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.authenticator; }

    @Override default void setConfig(JsonNode json, CloudService cloudService) {}

    @Override default CloudCredentials getCredentials() { return null; }

    @Override default void setCredentials(CloudCredentials creds) {}

}
