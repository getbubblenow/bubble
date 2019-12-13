package bubble.test.live;

import org.junit.Test;

public class LaunchSageTest extends LiveTestBase {

    @Override protected String getTestSageFqdn() { return null; }
    @Override protected boolean shouldStopSage() { return false; }

    @Override protected String getManifest() { return "manifest-live"; }

    @Test public void testLaunchSage () throws Exception {}

}
