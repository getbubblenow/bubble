package bubble.test;

import org.junit.Test;

public class DriverTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-proxy"; }

    @Test public void testListDrivers () throws Exception { modelTest("list_drivers"); }

}
