/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.live;

import bubble.notify.NewNodeNotification;
import bubble.test.system.NetworkTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

@Slf4j
public class LiveTestBase extends NetworkTestBase {

    @Override protected boolean useMocks() { return false; }

    protected String getTestSageFqdn() { return System.getenv("TEST_SAGE_FQDN"); }
    protected boolean shouldStopSage() { return true; }

    private static final AtomicReference<String> sageFqdn = new AtomicReference<>();
    private static final AtomicReference<LiveTestBase> testInstance = new AtomicReference<>();

    @Before public void startSage () throws Exception {
        synchronized (sageFqdn) {
            if (sageFqdn.get() == null) {
                final String envSage = getTestSageFqdn();
                if (envSage != null) {
                    sageFqdn.set(envSage);
                } else {
                    modelTest("live/fork_sage");
                    final NewNodeNotification newNetwork = (NewNodeNotification) getApiRunner().getContext().get("newNetwork");
                    if (newNetwork == null) fail("newNetwork not found in context after fork");
                    if (newNetwork.getFqdn() == null) fail("newNetwork.fqdn was null after fork");
                    sageFqdn.set(newNetwork.getFqdn());
                }
            }
        }
        getApiRunner().getContext().put("sageFqdn", sageFqdn.get());
    }

    @After public void saveTestInstance() throws Exception { testInstance.set(this); }

    @AfterClass public static void stopSage () throws Exception {
        final LiveTestBase liveTest = testInstance.get();
        if (liveTest == null) {
            log.warn("testInstance was never set, cannot stop sage: "+sageFqdn.get());
        } else {
            if (liveTest.shouldStopSage()) {
                final String fqdn = sageFqdn.get();
                if (fqdn == null) {
                    log.warn("stopSage: sage FQDN never got set");
                } else {
                    liveTest.modelTest("live/stop_sage");
                }
            }
        }
    }

}
