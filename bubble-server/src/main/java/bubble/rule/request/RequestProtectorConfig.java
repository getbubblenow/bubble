/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.request;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.TreeSet;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j @Accessors(chain=true)
public class RequestProtectorConfig {

    @Getter @Setter private Set<HeaderReplacement> headerReplacements = new TreeSet<>();
    public boolean hasHeaderReplacements() { return !empty(headerReplacements); }

    @NonNull public RequestProtectorConfig addHeaderReplacement(@NonNull final String regex,
                                                                @NonNull final String replacement) {
        headerReplacements.add(new HeaderReplacement(regex, replacement));
        return this;
    }

    @NonNull public RequestProtectorConfig removeHeaderReplacement(@NonNull final String regex) {
        if (hasHeaderReplacements()) headerReplacements.removeIf(r -> r.getRegex().equals(regex));
        return this;
    }
}
