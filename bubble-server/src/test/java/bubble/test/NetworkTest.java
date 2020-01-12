package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class NetworkTest extends NetworkTestBase {

    @Test public void testRegions       () throws Exception { modelTest("network/network_regions"); }
    @Test public void testUpgradeRole   () throws Exception { modelTest("network/upgrade_role"); }

}
