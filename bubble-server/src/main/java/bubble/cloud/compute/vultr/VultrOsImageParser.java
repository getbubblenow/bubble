package bubble.cloud.compute.vultr;

import bubble.cloud.compute.ListResourceParser;
import bubble.cloud.compute.OsImage;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class VultrOsImageParser extends ListResourceParser<OsImage> {

    @Override public OsImage parse(JsonNode item) {
        if (!item.has("OSID")) return die("parse: OSID not found");
        if (!item.has("name")) return die("parse: name not found");
        return new OsImage()
                .setId(item.get("OSID").asText())
                .setName(item.get("name").asText());
    }

}
