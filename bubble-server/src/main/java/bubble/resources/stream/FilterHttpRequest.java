package bubble.resources.stream;

import bubble.model.account.Account;
import bubble.model.device.Device;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpContentEncodingType;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class FilterHttpRequest {

    @Getter @Setter private String id;
    @Getter @Setter private FilterMatchersResponse matchersResponse;
    @Getter @Setter private Device device;
    @Getter @Setter private HttpContentEncodingType encoding;
    @Getter @Setter private Account account;
    @Getter @Setter private NameAndValue[] meta;
    @Getter @Setter private String contentType;

    @Getter @Setter private String[] matchers;

    public boolean hasMatchers() { return !empty(matchers); }

    public boolean hasMatcher (String matcherId) {
        if (empty(matcherId) || empty(matchers)) return false;
        return ArrayUtils.contains(matchers, matcherId);
    }
}
