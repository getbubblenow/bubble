/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.live;

import bubble.test.system.NetworkTestBase;
import org.junit.Test;

public class GoDaddyDnsTest extends NetworkTestBase {

    @Test public void testGoDaddyDns () throws Exception { modelTest("network/dns_crud"); }

}
