package bubble.model.cloud;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Accessors(chain=true)
public class BubbleVersionInfo {

    @Getter @Setter private String version;
    @Getter @Setter private String sha256;

    public boolean valid() { return !empty(version) && !empty(sha256); }

}
