package bubble.resources.stream;

import bubble.model.account.Account;
import bubble.model.device.Device;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang.ArrayUtils;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class FilterHttpRequest {

    @Getter @Setter private String id;
    @Getter @Setter private Device device;
    @Getter @Setter private Account account;
    @Getter @Setter private String[] matchers;
    @Getter @Setter private String contentType;

    public boolean hasMatcher (String matcherId) {
        if (empty(matcherId) || empty(matchers)) return false;
        return ArrayUtils.contains(matchers, matcherId);
    }

}
