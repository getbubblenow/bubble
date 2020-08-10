/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import bubble.server.BubbleConfiguration;
import bubble.test.ActivatedBubbleModelTestBase;
import org.cobbzilla.wizard.server.RestServer;

public class NetworkTestBase extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-network"; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        mockNetwork(server);
        super.beforeStart(server);
    }

}
