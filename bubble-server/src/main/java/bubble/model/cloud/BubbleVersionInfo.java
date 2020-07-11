package bubble.model.cloud;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.SemanticVersion.isNewerVersion;

@Accessors(chain=true) @EqualsAndHashCode(of={"version", "sha256"}) @ToString(of={"version", "sha256"})
public class BubbleVersionInfo {

    @Getter @Setter private String version;
    @Getter @Setter private String shortVersion;
    @Getter @Setter private String sha256;

    public boolean valid() { return !empty(version) && !empty(sha256); }

    public boolean newerThan(String otherVersion) { return isNewerVersion(otherVersion, version); }

    public boolean newerThan(BubbleVersionInfo info) { return newerThan(info.getVersion()); }

}
