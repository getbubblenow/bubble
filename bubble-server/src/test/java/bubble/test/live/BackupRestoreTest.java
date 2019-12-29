package bubble.test.live;

import org.junit.Test;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class BackupRestoreTest extends LiveTestBase {

    @Override protected String getTestSageFqdn() {
        final String fqdn = System.getenv("BUBBLE_BACKUP_RESTORE_SAGE");
        return empty(fqdn) ? die("getTestSageFqdn: BUBBLE_BACKUP_RESTORE_SAGE env var not defined") : fqdn;
    }
    @Override protected boolean shouldStopSage() { return false; }

    @Override public boolean backupsEnabled() { return true; }

    @Test public void testBackupAndRestore () throws Exception { modelTest("live/backup_and_restore"); }

}
