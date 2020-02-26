/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.storage;

import bubble.cloud.storage.s3.S3StorageDriver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.io.TempDir;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.io.FileUtil.abs;

@AllArgsConstructor @Slf4j
public class WriteRequestCleaner extends SimpleDaemon {

    private final Map<String, WriteRequest> requestMap;

    @Override protected long getSleepTime() { return HOURS.toMillis(1); }

    @Override protected void process() {
        try {
            final Set<String> toRemove = new HashSet<>();
            synchronized (requestMap) {
                for (Map.Entry<String, WriteRequest> entry : requestMap.entrySet()) {
                    final TempDir tempDir = entry.getValue().tempDir;
                    if (!tempDir.exists() || entry.getValue().age() > S3StorageDriver.STALE_REQUEST_TIMEOUT) {
                        toRemove.add(entry.getKey());
                    }
                }
            }
            for (String key : toRemove) {
                final WriteRequest req = requestMap.get(key);
                if (req.tempDir.exists() && !req.tempDir.delete()) {
                    log.warn("process: error deleting temp dir: "+abs(req.tempDir));
                }
                requestMap.remove(key);
            }
        } catch (Exception e) {
            log.error("process: "+e);
        }
    }
}
