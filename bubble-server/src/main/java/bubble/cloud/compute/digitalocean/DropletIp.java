package bubble.cloud.compute.digitalocean;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class DropletIp {

    @Getter @Setter private String ip_address;
    @Getter @Setter private Integer netmask;
    @Getter @Setter private String gateway;
    @Getter @Setter private String type;

}
