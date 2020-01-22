package bubble.resources.stream;

import bubble.model.account.Account;
import bubble.model.device.Device;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class FilterHttpRequest {

    @Getter @Setter private String id;
    @Getter @Setter private Device device;
    @Getter @Setter private Account account;
    @Getter @Setter private String[] matchers;
    @Getter @Setter private String contentType;

}
