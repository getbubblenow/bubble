/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class HeaderReplacement implements Comparable<HeaderReplacement> {

    public String getId() { return regex; }
    public void setId(String id) {} // noop

    @Getter @Setter private String regex;
    @Getter @Setter private String replacement;

    public HeaderReplacement(@NonNull final String regex, @NonNull final String replacement) {
        this.regex = regex;
        this.replacement = replacement;
    }

    @Override public int compareTo(@NonNull final HeaderReplacement o) {
        return getRegex().compareTo(o.getRegex().toLowerCase());
    }
}
