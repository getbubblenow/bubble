/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test;

import org.junit.Test;

public class DebugCallsTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-empty"; }

    @Test public void testEcho() throws Exception { modelTest("debug_echo"); }
}
