package bubble.app.bblock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class BlockListEntry {

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

}
