package bubble.rule.bblock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.util.collection.ArrayUtil;

@NoArgsConstructor
public class BubbleBlockConfig {

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

}
