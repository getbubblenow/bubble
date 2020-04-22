/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import org.junit.Test;

public class BackupTest extends NetworkTestBase {

    @Override public boolean backupsEnabled() { return true; }

    @Test public void testSimpleBackup  () throws Exception { modelTest("network/simple_backup"); }

}
