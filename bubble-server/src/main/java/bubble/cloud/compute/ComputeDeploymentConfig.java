package bubble.cloud.compute;

import lombok.Getter;
import lombok.Setter;

public class ComputeDeploymentConfig {

    public static final ComputeDeploymentConfig DEFAULT_DEPLOYMENT = new ComputeDeploymentConfig();

    @Getter @Setter private boolean sudo = true;
    @Getter @Setter private boolean hostname = true;

}
