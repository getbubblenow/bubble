package bubble.test.live;

import bubble.test.NetworkTestBase;
import org.junit.Test;

public class GoDaddyDnsTest extends NetworkTestBase {

    @Test public void testGoDaddyDns () throws Exception { modelTest("network/dns_crud"); }

}
