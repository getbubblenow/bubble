package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class NetworkTest extends NetworkTestBase {

    @Override protected String getNetworkStorageName() { return "S3_US_Standard"; }

    @Test public void testRegions       () throws Exception { modelTest("network/network_regions"); }
    @Test public void testSimpleNetwork () throws Exception { modelTest("network/simple_network"); }
    @Test public void testUpgradeRole   () throws Exception { modelTest("network/upgrade_role"); }
    @Test public void testSimpleBackup  () throws Exception { modelTest("network/simple_backup"); }
    @Test public void testGetNetworkKeys() throws Exception { modelTest("network/network_keys"); }

}
