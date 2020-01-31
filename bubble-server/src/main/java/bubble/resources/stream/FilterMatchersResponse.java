package bubble.resources.stream;

import bubble.model.app.AppMatcher;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchersResponse {

    public static final FilterMatchersResponse NO_MATCHERS = new FilterMatchersResponse();

    @Getter @Setter private Integer abort;
    @Getter @Setter private String device;
    @Getter @Setter private List<AppMatcher> matchers;
    @Getter @Setter private NameAndValue[] meta;
    public boolean hasMeta() { return !empty(meta); }

}
