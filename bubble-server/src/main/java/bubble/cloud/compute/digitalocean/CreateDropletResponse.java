package bubble.cloud.compute.digitalocean;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class CreateDropletResponse {

    @Getter @Setter private Droplet droplet;
    @Getter @Setter private JsonNode links;

}
