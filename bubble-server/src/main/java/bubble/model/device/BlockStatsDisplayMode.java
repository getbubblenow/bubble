package bubble.model.device;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BlockStatsDisplayMode {

    disabled, default_on, default_off;

    @JsonCreator public static BlockStatsDisplayMode fromString (String v) { return enumFromString(BlockStatsDisplayMode.class, v); }

    public boolean disabled () { return this == disabled; }
    public boolean enabled () { return !disabled(); }

}
