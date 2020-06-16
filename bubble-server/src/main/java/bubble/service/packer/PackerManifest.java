/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.packer;

import lombok.Getter;
import lombok.Setter;

public class PackerManifest {

    @Getter @Setter private PackerBuild[] builds;
    @Getter @Setter private String last_run_uuid;

}
