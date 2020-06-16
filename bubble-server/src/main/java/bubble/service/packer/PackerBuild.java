/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.packer;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.PackerImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public class PackerBuild {

    @Getter @Setter private String name;
    @Getter @Setter private String builder_type;
    @Getter @Setter private Long build_time;
    @Getter @Setter private ArrayNode files;
    @Getter @Setter private String artifact_id;
    @Getter @Setter private String packer_run_uuid;
    @Getter @Setter private JsonNode custom_data;

    public PackerImage toPackerImage(String name) {
        final String[] parts = artifact_id.split(":");
        final CloudRegion[] regions = (CloudRegion[]) Arrays.stream(parts[0].split(",")).map(r -> new CloudRegion().setInternalName(r)).toArray();
        final String id = parts[1];
        return new PackerImage()
                .setId(id)
                .setName(name)
                .setRegions(regions);
    }
}
