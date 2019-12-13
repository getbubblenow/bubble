package bubble.model.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class RekeyRequest {

    @Getter @Setter private String password;
    @Getter @Setter private String newCloud;

}
