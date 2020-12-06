/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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

    @Getter @Setter private JsonNode post;
    public boolean hasPost () { return post != null; }

    @Getter @Setter private Boolean sudo;
    public boolean sudo () { return sudo == null || sudo; }

}
