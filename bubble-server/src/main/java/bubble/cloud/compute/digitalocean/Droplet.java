package bubble.cloud.compute.digitalocean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public class Droplet {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private int memory;
    @Getter @Setter private int vcpus;
    @Getter @Setter private int disk;
    @Getter @Setter private Boolean locked;
    @Getter @Setter private String status;
    @Getter @Setter private JsonNode kernel;
    @Getter @Setter private String created_at;
    @Getter @Setter private String[] features;
    @Getter @Setter private String[] backup_ids;
    @Getter @Setter private String[] snapshot_ids;
    @Getter @Setter private String[] volume_ids;
    @Getter @Setter private JsonNode image;
    @Getter @Setter private JsonNode size;
    @Getter @Setter private JsonNode region;

    @Getter @Setter private String[] tags;

    public String getTagWithPrefix (String prefix) {
        return tags == null ? null : Arrays.stream(tags).filter(t -> t.startsWith(prefix)).findFirst().orElse(null);
    }
    public boolean hasTagWithPrefix (String prefix) {
        return tags != null && Arrays.stream(tags).anyMatch(t -> t.startsWith(prefix));
    }

    @Getter @Setter private DropletNetInfo networks;

    @JsonIgnore public String getIp4() { return networks == null ? null : networks.getIp4(); }
    public boolean hasIp4 () { return getIp4() != null; }

    @JsonIgnore public String getIp6() { return networks == null ? null : networks.getIp6(); }
    public boolean hasIp6 () { return getIp6() != null; }

}
