/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.vultr;

import bubble.cloud.compute.ComputeDiskType;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ListResourceParser;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class VultrComputeNodeSizeParser extends ListResourceParser<ComputeNodeSize> {

    @Override public ComputeNodeSize parse(JsonNode item) {
        if (!item.has("VPSPLANID")) return die("parse: VPSPLANID not found");
        if (!item.has("name")) return die("parse: name not found");
        if (!item.has("vcpu_count")) return die("parse: vcpu_count not found");
        if (!item.has("ram")) return die("parse: ram not found");
        if (!item.has("disk")) return die("parse: disk not found");
        if (!item.has("bandwidth_gb")) return die("parse: bandwidth_gb not found");
        return new ComputeNodeSize()
                .setId(item.get("VPSPLANID").asLong())
                .setInternalName(item.get("name").textValue())
                .setVcpu(item.get("vcpu_count").asInt())
                .setMemoryMB(item.get("ram").asInt())
                .setDiskGB(item.get("disk").asInt())
                .setDiskType(ComputeDiskType.ssd)
                .setTransferGB(item.get("bandwidth_gb").asInt());
    }

}
