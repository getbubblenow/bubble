package bubble.cloud.dns.route53;

import com.amazonaws.regions.Regions;
import lombok.Getter;
import lombok.Setter;

public class Route53DnsConfig {

    @Getter @Setter private Regions region = Regions.DEFAULT_REGION;

}
