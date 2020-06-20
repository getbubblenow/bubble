/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.PackerImage;
import bubble.cloud.compute.PackerImageParserBase;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class DigitalOceanPackerImageParser extends PackerImageParserBase {

    public DigitalOceanPackerImageParser (String bubbleVersion, String keyHash) {
        super(bubbleVersion, keyHash);
    }

    @Override public boolean allowEmpty() { return true; }

    @Override public PackerImage parse(JsonNode item) {

        if (!item.has("name")) return null;
        final String name = item.get("name").textValue();
        if (!isValidPackerImage(name)) return null;

        final PackerImage image = new PackerImage().setName(name);

        if (item.has("id")) image.setId(item.get("id").asText());

        if (item.has("regions")) {
            final JsonNode regionsNode = item.get("regions");
            if (regionsNode.isArray()) {
                final List<CloudRegion> regions = new ArrayList<>();
                for (int i=0; i<regionsNode.size(); i++) {
                    final String regionName = regionsNode.get(i).textValue();
                    regions.add(new CloudRegion().setInternalName(regionName));
                }
                image.setRegions(regions.toArray(CloudRegion.EMPTY_REGIONS));
            }
        }
        return image;
    }

}
