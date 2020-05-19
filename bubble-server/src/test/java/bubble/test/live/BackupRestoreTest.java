/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.live;

import org.junit.Test;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.URIUtil.hostToDomain;

public class BackupRestoreTest extends LiveTestBase {

    @Override protected String getTestSageFqdn() {
        final String fqdn = System.getenv("BUBBLE_BACKUP_RESTORE_SAGE");
        return empty(fqdn) ? die("getTestSageFqdn: BUBBLE_BACKUP_RESTORE_SAGE env var not defined") : fqdn;
    }
    // If not default, remember to set `TEST_SAGE_ROOT_PASS` properly also.

    @Override protected boolean shouldStopSage() { return false; }

    @Override public boolean backupsEnabled() { return true; }

    @Override public String getDefaultDomain() { return hostToDomain(getTestSageFqdn()); }

    @Test public void testBackupAndRestore () throws Exception { modelTest("live/backup_and_restore"); }

}
