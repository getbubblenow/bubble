/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.filter;

import bubble.test.system.NetworkTestBase;
import org.junit.Test;

public class TrafficAnalyticsTest extends NetworkTestBase {

    @Override protected String getManifest() { return "manifest-analytics"; }

    @Test public void testTrafficAnalytics () throws Exception { modelTest("filter/traffic_analytics"); }

}
