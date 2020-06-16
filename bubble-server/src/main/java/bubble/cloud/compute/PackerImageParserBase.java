package bubble.cloud.compute;

import static bubble.service.packer.PackerJob.PACKER_IMAGE_PREFIX;

public abstract class PackerImageParserBase extends ListResourceParser<PackerImage> {

    private String bubbleVersion;
    private String keyHash;

    public PackerImageParserBase(String bubbleVersion, String keyHash) {
        this.bubbleVersion = bubbleVersion;
        this.keyHash = keyHash;
    }

    public boolean isValidPackerImage(String name) {
        if (!name.startsWith(PACKER_IMAGE_PREFIX)) return false;
        if (!name.contains("_"+bubbleVersion+"_")) return false;
        if (!name.contains("_"+keyHash+"_")) return false;
        return true;
    }

}
