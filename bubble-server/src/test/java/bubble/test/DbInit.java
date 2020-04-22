/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test;

import bubble.dao.app.BubbleAppDAO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;

import static org.cobbzilla.util.io.FileUtil.abs;
import static org.junit.Assert.assertEquals;

@Slf4j
public class DbInit extends BubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-empty"; }

    @Override protected boolean createSqlIndexes() { return true; }

    @Test public void initDatabase () {
        assertEquals(0, getConfiguration().getBean(BubbleAppDAO.class).findAll().size());
        final String dumpFile = System.getProperty("db.dump");
        if (dumpFile != null) {
            System.out.println("Dumped DB to: "+abs(getConfiguration().pgDump(new File(dumpFile))));
        } else {
            System.out.println("Dumped DB to: "+abs(getConfiguration().pgDump()));
        }
    }

}
