package bubble.test.system;

import org.junit.Test;

public class BackupTest extends NetworkTestBase {

    @Override public boolean backupsEnabled() { return true; }

    @Test public void testSimpleBackup  () throws Exception { modelTest("network/simple_backup"); }

}
