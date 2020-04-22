/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.live;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.dns.godaddy.GoDaddyDnsDriver;
import org.junit.Test;

import static bubble.ApiConstants.getBubbleDefaultDomain;

public class LaunchSageTest extends LiveTestBase {

    @Override protected String getManifest() { return "manifest-live"; }

    @Override protected Class<? extends CloudServiceDriver> getPublicDnsDriver() { return GoDaddyDnsDriver.class; }

    @Override protected String getTestSageFqdn() { return null; }
    @Override protected boolean shouldStopSage() { return false; }
    @Override public String getDefaultDomain() { return getBubbleDefaultDomain(); }

    @Test public void testLaunchSage () {}

}
