/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import static bubble.service.packer.PackerJob.PACKER_IMAGE_PREFIX;

public abstract class PackerImageParserBase extends ListResourceParser<PackerImage> {

    private final String bubbleVersion;
    private final String versionHash;

    public PackerImageParserBase(String bubbleVersion, String versionHash) {
        this.bubbleVersion = bubbleVersion;
        this.versionHash = versionHash;
    }

    public boolean isValidPackerImage(String name) {
        if (!name.startsWith(PACKER_IMAGE_PREFIX)) return false;
        if (!name.contains("_"+bubbleVersion+"_")) return false;
        if (!name.contains("_"+versionHash+"_")) return false;
        return true;
    }

}
