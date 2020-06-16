package bubble.cloud.compute;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.NameAndValue;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;

public class PackerConfig {

    @Getter @Setter private NameAndValue[] vars;

    @Getter @Setter private Boolean iterateRegions;
    public boolean iterateRegions() { return bool(iterateRegions); }

    @Getter @Setter private JsonNode builder;

}
