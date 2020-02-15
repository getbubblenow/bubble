package bubble.test.filter;

import bubble.test.system.NetworkTestBase;
import org.junit.Test;

public class TrafficAnalyticsTest extends NetworkTestBase {

    @Override protected String getManifest() { return "manifest-analytics"; }

    @Test public void testTrafficAnalytics () throws Exception { modelTest("filter/traffic_analytics"); }

}
