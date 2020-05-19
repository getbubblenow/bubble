/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.live;

import bubble.notify.NewNodeNotification;
import bubble.test.system.NetworkTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.junit.Assert.fail;

@Slf4j
public class LiveTestBase extends NetworkTestBase {

    @Override protected boolean useMocks() { return false; }

    protected String getTestSageFqdn() { return System.getenv("TEST_SAGE_FQDN"); }
    protected String getTestSageRootPass() {
        final var envRootPass = System.getenv("TEST_SAGE_ROOT_PASS");
        return (!empty(envRootPass)) ? envRootPass : ROOT_PASSWORD;
    }
    protected boolean shouldStopSage() { return true; }

    private static final AtomicReference<AbstractMap.SimpleEntry<String, String>> sageFqdnAndRootPass =
            new AtomicReference<>();
    private static final AtomicReference<LiveTestBase> testInstance = new AtomicReference<>();

    @Before public void startSage () throws Exception {
        synchronized (sageFqdnAndRootPass) {
            if (sageFqdnAndRootPass.get() == null) {
                final var envSage = getTestSageFqdn();
                final var rootPass = getTestSageRootPass();
                if (envSage != null) {
                    sageFqdnAndRootPass.set(new AbstractMap.SimpleEntry<>(envSage, rootPass));
                } else {
                    modelTest("live/fork_sage");
                    final var newNetwork = (NewNodeNotification) getApiRunner().getContext().get("newNetwork");
                    if (newNetwork == null) fail("newNetwork not found in context after fork");
                    if (newNetwork.getFqdn() == null) fail("newNetwork.fqdn was null after fork");
                    sageFqdnAndRootPass.set(new AbstractMap.SimpleEntry<>(newNetwork.getFqdn(), rootPass));
                }
            }
        }
        getApiRunner().getContext().put("sageFqdn", sageFqdnAndRootPass.get().getKey());
        getApiRunner().getContext().put("sageRootPass", sageFqdnAndRootPass.get().getValue());
    }

    @After public void saveTestInstance() throws Exception { testInstance.set(this); }

    @AfterClass public static void stopSage () throws Exception {
        final LiveTestBase liveTest = testInstance.get();
        if (liveTest == null) {
            log.warn("testInstance was never set, cannot stop sage: " + sageFqdnAndRootPass.get());
        } else {
            if (liveTest.shouldStopSage()) {
                final var fqdn = sageFqdnAndRootPass.get().getKey();
                if (fqdn == null) {
                    log.warn("stopSage: sage FQDN never got set");
                } else {
                    liveTest.modelTest("live/stop_sage");
                }
            }
        }
    }

}
