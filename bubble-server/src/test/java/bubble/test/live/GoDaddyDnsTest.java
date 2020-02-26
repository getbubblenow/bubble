/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.live;

import bubble.test.system.NetworkTestBase;
import org.junit.Test;

public class GoDaddyDnsTest extends NetworkTestBase {

    @Test public void testGoDaddyDns () throws Exception { modelTest("network/dns_crud"); }

}
