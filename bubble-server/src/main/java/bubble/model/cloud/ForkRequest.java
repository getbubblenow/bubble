package bubble.model.cloud;

import lombok.Getter;
import lombok.Setter;

public class ForkRequest {

    @Getter @Setter private String fqdn;
    @Getter @Setter private String adminEmail;
    @Getter @Setter private String cloud;
    @Getter @Setter private String region;
    @Getter @Setter private Boolean exactRegion;

}
