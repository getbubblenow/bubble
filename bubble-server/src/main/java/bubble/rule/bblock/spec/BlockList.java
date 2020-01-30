package bubble.rule.bblock.spec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class BlockList {

    @Getter private Set<BlockSpec> blacklist = new HashSet<>();
    @Getter private Set<BlockSpec> whitelist = new HashSet<>();

    public void addToWhitelist(BlockSpec spec) {
        whitelist.add(spec);
    }

    public void addToWhitelist(List<BlockSpec> specs) {
        for (BlockSpec spec : specs) addToWhitelist(spec);
    }

    public void addToBlacklist(BlockSpec spec) {
        blacklist.add(spec);
    }

    public void addToBlacklist(List<BlockSpec> specs) {
        for (BlockSpec spec : specs) addToBlacklist(spec);
    }

    public void merge(BlockList other) {
        for (BlockSpec allow : other.getWhitelist()) {
            addToWhitelist(allow);
        }
        for (BlockSpec block : other.getBlacklist()) {
            addToBlacklist(block);
        }
    }

    public BlockDecision getDecision(String fqdn, String path) {
        for (BlockSpec allow : whitelist) {
            if (allow.matches(fqdn, path)) return BlockDecision.ALLOW;
        }
        final BlockDecision decision = new BlockDecision();
        for (BlockSpec block : blacklist) {
            if (block.matches(fqdn, path)) {
                if (!block.hasSelector()) return BlockDecision.BLOCK;
                decision.add(block);
            }
        }
        return decision;
    }

}
