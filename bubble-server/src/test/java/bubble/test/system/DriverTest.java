/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import bubble.test.ActivatedBubbleModelTestBase;
import org.junit.Test;

public class DriverTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-proxy"; }

    @Test public void testListDrivers () throws Exception { modelTest("list_drivers"); }

}
