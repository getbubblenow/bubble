/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ListResourceParser;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class DigitalOceanRegionParser extends ListResourceParser<CloudRegion> {

    @Override public CloudRegion parse(JsonNode item) {
        if (!item.has("name")) return die("parse: name not found");
        if (!item.has("slug")) return die("parse: slug not found");
        return new CloudRegion()
                .setName(item.get("name").textValue())
                .setInternalName(item.get("slug").textValue());
    }

}
