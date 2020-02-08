package bubble.rule.bblock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor @Slf4j
public class BubbleBlockConfig {

    @Getter @Setter private Boolean inPageBlocks;
    public boolean inPageBlocks() { return inPageBlocks != null && inPageBlocks; }

    @Getter @Setter private BubbleBlockList[] blockLists;

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
            blockLists = retained.toArray(new BubbleBlockList[0]);
        } else {
            log.warn("removeList: list not found, not removed: "+list.id());
        }
        return this;
    }
}
