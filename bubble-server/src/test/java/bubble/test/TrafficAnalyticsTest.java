package bubble.test;

import org.junit.Test;

public class TrafficAnalyticsTest extends NetworkTestBase {

    @Override protected String getManifest() { return "manifest-live"; }

    @Test public void testTrafficAnalytics () throws Exception { modelTest("filter/traffic_analytics"); }

}
