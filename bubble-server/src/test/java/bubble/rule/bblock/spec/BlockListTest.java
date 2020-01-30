package bubble.rule.bblock.spec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BlockListTest {

    @Test public void testBlanketBlock () throws Exception {
        final BlockList blockList = new BlockList();
        blockList.addToBlacklist(BlockSpec.parse("||fredfiber.no^"));
        assertEquals("expected block", BlockDecisionType.block, blockList.getDecision("fredfiber.no", "/somepath").getDecisionType());
    }
}
