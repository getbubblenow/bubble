package bubble.test.system;

import bubble.test.ActivatedBubbleModelTestBase;
import org.junit.Test;

public class DriverTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-proxy"; }

    @Test public void testListDrivers () throws Exception { modelTest("list_drivers"); }

}
