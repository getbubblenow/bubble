/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.storage;

import lombok.ToString;
import org.cobbzilla.util.io.TempDir;

import java.io.File;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@ToString
public class WriteRequest {

    public TempDir tempDir;
    public File file;
    public String requestId;
    public long ctime = now();
    public long age() { return now() - ctime; }

    public WriteRequest(TempDir tempDir, File file, String requestId) {
        this.tempDir = tempDir;
        this.file = file;
        this.requestId = requestId;
    }

}
