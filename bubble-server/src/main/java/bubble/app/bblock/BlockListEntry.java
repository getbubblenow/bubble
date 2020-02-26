/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.app.bblock;

import lombok.*;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.safeFunctionName;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"rule"})
public class BlockListEntry implements Comparable<BlockListEntry> {

    public static BlockListEntry sourceRule(String rule) {
        return new BlockListEntry(BlockListEntryType.builtin, rule);
    }

    public static BlockListEntry additionalRule(String rule) {
        return new BlockListEntry(BlockListEntryType.custom, rule);
    }

    public static String idFor(String rule) { return sha256_hex(rule); }

    public String getId() { return idFor(rule); }
    public void setId(String id) {} // noop

    @Getter @Setter private BlockListEntryType ruleType;
    @Getter @Setter private String rule;

    @Override public int compareTo(BlockListEntry o) {
        if (rule == null) return o.rule == null ? 0 : -1;
        if (o.rule == null) return 1;
        return normalizedString(rule).compareTo(normalizedString(o.rule));
    }

    public String normalizedString(String rule) {
        return safeFunctionName(rule.replace("www.", ""));
    }

}
