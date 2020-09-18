/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.cloud.BubbleNetwork;
import bubble.service.boot.SelfNodeService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class MitmControlService {

    @Autowired private SelfNodeService selfNodeService;

    public static final File MITM_CONTROL_FILE = new File("/home/bubble/.mitm_monitor");
    public static final File MITM_ROOT_CONTROL_FILE = new File("/usr/share/bubble/mitm_monitor");

    // must be longer than the sleep time in mitm_monitor.sh (currently 5 seconds)
    private static final long MITM_CONTROL_TIMEOUT = SECONDS.toMillis(10);
    private static final long MITM_MONITOR_SLEEP = SECONDS.toMillis(1);

    public boolean getEnabled() {
        checkMitmInstalled();
        try {
            final String rootValue = getRootValue();
            switch (rootValue) {
                case "on": return true;
                case "off": return false;
            }
        } catch (Exception e) {
            reportError(getClass().getSimpleName()+".getEnabled(): error reading MITM state");
        }
        throw invalidEx("err.mitm.errorReadingControlFile");
    }

    public synchronized Boolean setEnabled(boolean enable) {
        checkMitmInstalled();
        boolean ok = false;
        try {
            final String rootValue = getRootValue();
            final String currentValue = getCurrentValue();
            if (!currentValue.equals(rootValue)) {
                throw invalidEx("err.mitm.changeInProgress");
            }
            if ((enable && rootValue.equals("on")) || !enable && rootValue.equals("off")) {
                log.debug("setEnabled("+enable+"): no change in state");
                return enable;
            }

            final String desiredValue = enable ? "on" : "off";
            FileUtil.toFile(MITM_CONTROL_FILE, desiredValue);
            final long start = now();
            while (now() - start < MITM_CONTROL_TIMEOUT && !getRootValue().equals(desiredValue)) {
                sleep(MITM_MONITOR_SLEEP, "waiting for MITM control update");
            }
            if (getRootValue().equals(desiredValue)) {
                ok = true;
                return enable;
            }
            throw invalidEx("err.mitm.errorWritingControlFile");

        } catch (IOException e) {
            throw invalidEx("err.mitm.errorWritingControlFile");

        } finally {
            if (!ok) reportError(getClass().getSimpleName()+".setEnabled("+enable+"): error controlling MITM");
        }
    }

    public void checkMitmInstalled() {
        final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();
        if (thisNetwork == null || thisNetwork.notNode()) {
            throw invalidEx("err.mitm.notInstalled");
        }
    }

    public String getCurrentValue() throws IOException { return FileUtil.toString(MITM_CONTROL_FILE).trim(); }
    public String getRootValue() throws IOException { return FileUtil.toString(MITM_ROOT_CONTROL_FILE).trim(); }

}
