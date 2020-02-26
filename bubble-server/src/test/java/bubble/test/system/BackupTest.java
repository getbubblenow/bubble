/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.test.system;

import org.junit.Test;

public class BackupTest extends NetworkTestBase {

    @Override public boolean backupsEnabled() { return true; }

    @Test public void testSimpleBackup  () throws Exception { modelTest("network/simple_backup"); }

}
