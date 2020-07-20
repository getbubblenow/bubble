/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class NetworkTest extends NetworkTestBase {

    @Test public void testRegions        () throws Exception { modelTest("network/network_regions"); }
    @Test public void testGetNetworkKeys () throws Exception { modelTest("network/network_keys"); }

}
