package bubble.resources.stream;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchersRequest {

    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;
    @Getter @Setter private String remoteAddr;

}
