/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.system;

import bubble.test.ActivatedBubbleModelTestBase;
import org.junit.Test;

public class DriverTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-proxy"; }

    @Test public void testListDrivers () throws Exception { modelTest("list_drivers"); }

}
