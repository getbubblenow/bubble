package bubble.app.bblock;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BlockListEntryType {

    builtin, custom;

    @JsonCreator public static BlockListEntryType fromString (String v) { return enumFromString(BlockListEntryType.class, v); }

}
