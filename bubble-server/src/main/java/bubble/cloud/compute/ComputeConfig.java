/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import bubble.cloud.RegionalConfig;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.NameAndValue;

public class ComputeConfig extends RegionalConfig {

    @Getter @Setter private ComputeNodeSize[] sizes;
    @Getter @Setter private NameAndValue[] config;

    public CloudRegion getRegion (String name) {
        for (CloudRegion r : getRegions()) {
            if (r.getName().equalsIgnoreCase(name) || r.getInternalName().equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    public ComputeNodeSize getSize (String name) {
        for (ComputeNodeSize s : getSizes()) {
            if (s.getName().equalsIgnoreCase(name) || s.getInternalName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    public String getConfig(String name) { return config == null ? null : NameAndValue.find(config, name); }

}
