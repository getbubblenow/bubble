/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.system;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class NetworkTest extends NetworkTestBase {

    @Test public void testRegions       () throws Exception { modelTest("network/network_regions"); }
    @Test public void testUpgradeRole   () throws Exception { modelTest("network/upgrade_role"); }
    @Test public void testGetNetworkKeys() throws Exception { modelTest("network/network_keys"); }


}
