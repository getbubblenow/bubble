/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.rule.RequestModifierConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Slf4j
public class BubbleBlockConfig extends RequestModifierConfig {

    @Getter @Setter private Boolean inPageBlocks;
    public boolean inPageBlocks() { return bool(inPageBlocks); }

    @Getter @Setter private BubbleUserAgentBlock[] userAgentBlocks;
    public boolean hasUserAgentBlocks () { return !empty(userAgentBlocks); }

    @Getter @Setter private BubbleBlockStatsDisplayList[] statsDisplayLists;
    public boolean hasStatsDisplayLists () { return !empty(statsDisplayLists); }
    public void addStatsDisplayList(BubbleBlockStatsDisplayList list) { statsDisplayLists = ArrayUtil.append(statsDisplayLists, list); }
    public boolean hasStatsDisplayList(BubbleBlockStatsDisplayList list) {
        return hasStatsDisplayLists() && Arrays.stream(statsDisplayLists).anyMatch(l -> l.getUrl().equals(list.getUrl()));
    }

    @Getter @Setter private BubbleBlockList[] blockLists;
    public boolean hasBlockLists () { return !empty(blockLists); }
    public void addBlockList(BubbleBlockList list) { blockLists = ArrayUtil.append(blockLists, list); }
    public boolean hasBlockList(BubbleBlockList list) {
        return hasBlockLists() && Arrays.stream(blockLists).anyMatch(l -> l.getUrl().equals(list.getUrl()));
    }

    public BubbleBlockConfig updateList(BubbleBlockList list) {
        if (blockLists == null) {
            blockLists = new BubbleBlockList[] {list};
            return this;
        }
        for (int i=0; i<blockLists.length; i++) {
            if (blockLists[i].getId().equals(list.getId())) {
                blockLists[i] = list;
                return this;
            }
        }
        blockLists = ArrayUtil.append(blockLists, list);
        return this;
    }

    public BubbleBlockConfig removeList(BubbleBlockList list) {
        if (blockLists == null || blockLists.length == 0) {
            log.warn("removeList: no lists, cannot remove: "+list.id());
        }

        final List<BubbleBlockList> retained = new ArrayList<>();
        for (BubbleBlockList bbl : blockLists) {
            if (!bbl.getId().equals(list.getId())) retained.add(bbl);
        }

        if (retained.size() < blockLists.length) {
            blockLists = retained.toArray(BubbleBlockList[]::new);
        } else {
            log.warn("removeList: list not found, not removed: "+list.id());
        }
        return this;
    }

    public BubbleBlockConfig addUserAgentBlock (BubbleUserAgentBlock uaBlock) {
        if (userAgentBlocks != null) {
            for (BubbleUserAgentBlock uab : userAgentBlocks) {
                if (uab.equals(uaBlock)) return this;
            }
        }
        userAgentBlocks = ArrayUtil.append(userAgentBlocks, uaBlock);
        return this;
    }

    public BubbleBlockConfig removeUserAgentBlock (String id) {
        if (userAgentBlocks == null) return this;
        final List<BubbleUserAgentBlock> retained = new ArrayList<>(userAgentBlocks.length);
        for (BubbleUserAgentBlock uab : userAgentBlocks) {
            if (!uab.getId().equals(id)) retained.add(uab);
        }
        userAgentBlocks = retained.toArray(BubbleUserAgentBlock.NO_BLOCKS);
        return this;
    }

}
