package bubble.abp;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BlockListType {

    ads, malware, privacy, clickbait, gambling, phishing, nsfw, crypto, annoyances, custom;

    @JsonCreator public BlockListType fromString (String v) { return valueOf(v.toLowerCase()); }

}
