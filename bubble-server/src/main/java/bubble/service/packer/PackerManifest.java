package bubble.service.packer;

import lombok.Getter;
import lombok.Setter;

public class PackerManifest {

    @Getter @Setter private PackerBuild[] builds;
    @Getter @Setter private String last_run_uuid;

}
