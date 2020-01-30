package bubble.rule.bblock.spec;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BlockDecisionType {

    block, allow, filter;

    @JsonCreator public static BlockDecisionType fromString (String v) { return enumFromString(BlockDecisionType.class, v); }

}
