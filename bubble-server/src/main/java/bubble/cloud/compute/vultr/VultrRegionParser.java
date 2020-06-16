/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.vultr;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ListResourceParser;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class VultrRegionParser extends ListResourceParser<CloudRegion> {

    @Override public CloudRegion parse(JsonNode item) {
        if (!item.has("DCID")) return die("parse: DCID not found");
        if (!item.has("name")) return die("parse: name not found");
        return new CloudRegion()
                .setId(item.get("DCID").asLong())
                .setInternalName(item.get("name").textValue());
    }

}
