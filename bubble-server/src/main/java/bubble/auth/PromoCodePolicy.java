package bubble.auth;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum PromoCodePolicy {

    disabled, optional, required;

    @JsonCreator public static PromoCodePolicy fromString (String v) { return enumFromString(PromoCodePolicy.class, v); }

    public boolean disabled () { return this == disabled; }
    public boolean enabled  () { return !disabled(); }
    public boolean required () { return this == required; }

}