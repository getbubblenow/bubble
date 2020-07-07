/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.regex.Pattern;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@Accessors(chain=true)
public class NodeProgressMeterTick {

    public enum TickMatchType {
        exact, prefix, regex;
        @JsonCreator public static TickMatchType fromString(String v) { return enumFromString(TickMatchType.class, v); }
    }

    public NodeProgressMeterTick() {
        this.ctime = now();
    }

    @Setter private Long ctime;
    // backward compatibility - the following getter may be removed and default one may be used after some time, while
    // ctime can be changed to be of simple `long` type
    public long getCtime() { return ctime == null ? 0 : ctime; }

    @Getter @Setter private String account;
    public boolean hasAccount() { return !empty(account); }

    @Getter @Setter private String network;
    public boolean hasNetwork() { return !empty(network); }

    @Getter @Setter private String pattern;
    @JsonIgnore @Getter(lazy=true) private final Pattern _pattern = Pattern.compile(getPattern());

    @Getter @Setter private TickMatchType match;
    public TickMatchType match() { return match != null ? match : TickMatchType.regex; }

    @Getter @Setter private Integer percent;
    @Getter @Setter private String messageKey;
    @Getter @Setter private String details;

    public boolean matches(String line) {
        switch (match()) {
            case exact:          return line.trim().equals(getPattern().trim());
            case prefix:         return line.trim().startsWith(getPattern().trim());
            case regex: default: return get_pattern().matcher(line).matches();
        }
    }
}
