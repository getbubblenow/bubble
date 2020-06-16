package bubble.cloud.compute.digitalocean;

import bubble.cloud.compute.ListResourceParser;
import bubble.cloud.compute.OsImage;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class DigitalOceanOsImageParser extends ListResourceParser<OsImage> {

    @Override public OsImage parse(JsonNode item) {
        final OsImage image = new OsImage();
        if (item.has("id")) {
            final JsonNode id = item.get("id");
            if (id.isNumber()) {
                image.setId(id.asText());
            } else {
                return die("parse: id was not numeric");
            }
        } else {
            return die("parse: id not found");
        }
        if (item.has("slug")) {
            final JsonNode name = item.get("slug");
            image.setName(name.asText());
        } else {
            return die("parse: name not found");
        }
        return image;
    }
}
