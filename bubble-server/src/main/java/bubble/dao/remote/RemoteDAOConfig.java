package bubble.dao.remote;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class RemoteDAOConfig {

    @Getter @Setter private String uriBase;
    @Getter @Setter private String sessionId;

}
