package bubble.cloud.compute.vultr;

import bubble.cloud.compute.PackerImage;
import bubble.cloud.compute.PackerImageParserBase;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class VultrPackerImageParser extends PackerImageParserBase {

    public VultrPackerImageParser(String bubbleVersion, String keyHash) {
        super(bubbleVersion, keyHash);
    }

    @Override public PackerImage parse(JsonNode item) {
        if (!item.has("SNAPSHOTID")) return die("parse: SNAPSHOTID not found");
        if (!item.has("OSID")) return die("parse: OSID not found");
        if (!item.has("description")) return die("parse: description not found");
        if (!item.has("status")) return die("parse: status not found");
        if (!item.get("status").textValue().equals("complete")) return null;
        final String name = item.get("description").textValue();
        if (!isValidPackerImage(name)) return null;
        return new PackerImage()
                .setName(name)
                .setId(item.get("SNAPSHOTID").textValue());
    }

}
