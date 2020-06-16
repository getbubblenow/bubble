package bubble.cloud.compute.digitalocean;

import bubble.cloud.compute.ComputeDiskType;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ListResourceParser;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class DigitalOceanComputeNodeSizeParser extends ListResourceParser<ComputeNodeSize> {

    @Override public ComputeNodeSize parse(JsonNode item) {
        if (!item.has("slug")) return die("parse: slug not found");
        if (!item.has("vcpus")) return die("parse: vcpus not found");
        if (!item.has("memory")) return die("parse: memory not found");
        if (!item.has("disk")) return die("parse: disk not found");
        if (!item.has("transfer")) return die("parse: transfer not found");
        return new ComputeNodeSize()
                .setInternalName(item.get("slug").textValue())
                .setVcpu(item.get("vcpus").intValue())
                .setMemoryMB(item.get("memory").intValue())
                .setDiskGB(item.get("disk").intValue())
                .setDiskType(ComputeDiskType.ssd)
                .setTransferGB(1024 * item.get("transfer").intValue());
    }

}
