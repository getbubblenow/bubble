/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.storage;

import bubble.cloud.CloudServiceDriverBase;
import bubble.model.cloud.StorageMetadata;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.io.TempDir;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

public abstract class StorageServiceDriverBase<T> extends CloudServiceDriverBase<T> implements StorageServiceDriver {

    private static final Map<String, WriteRequest> requestMap = new ConcurrentHashMap<>();

    private static final Map<String, WriteRequestCleaner> cleaners = new ConcurrentHashMap<>();

    @Override public void postSetup() {
        final String key = sha256_hex(json(getConfig())+":"+json(getCredentials()));
        synchronized (cleaners) {
            WriteRequestCleaner cleaner = cleaners.get(key);
            if (cleaner == null) {
                cleaner = new WriteRequestCleaner(requestMap);
                cleaners.put(key, cleaner);
                cleaner.start();
            }
        }
    }

    public WriteRequest findWriteRequest(String requestId) { return requestMap.get(requestId); }

    protected void cleanup(WriteRequest writeRequest) {
        final WriteRequest found = requestMap.remove(writeRequest.requestId);
        if (found != null) {
            if (!found.tempDir.delete()) log.error("cleanup: error removing temp dir: " + abs(found.tempDir));
        } else {
            log.warn("cleanup: request not found, deleting tempDir anyway");
            if (!writeRequest.tempDir.delete()) log.error("cleanup: error removing temp dir: " + abs(writeRequest.tempDir));
        }
    }

    @Override public boolean _write(String fromNode, String key, InputStream data, StorageMetadata metadata, String requestId) throws IOException {
        final TempDir temp;
        final File f;
        WriteRequest writeRequest;
        synchronized (requestMap) {
            writeRequest = requestMap.get(requestId);
            if (writeRequest == null) {
                // write file to disk so we can retry
                temp = new TempDir();
                f = new File(temp, "spooled");
                writeRequest = new WriteRequest(temp, f, requestId);
                requestMap.put(requestId, writeRequest);
                long countBytes = 0;
                try (OutputStream out = new FileOutputStream(f)) {
                    countBytes = IOUtils.copyLarge(data, out);
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("timeout")) {
                        if (countBytes == 0) {
                            return die("StorageServiceDriverBase._write: error (no bytes) ", e);
                        }
                    }
                } catch (Exception e) {
                    return die("StorageServiceDriverBase._write: exception ", e);
                }
            }
        }
        final boolean success = writeStorage(fromNode, key, writeRequest, metadata);
        if (success) cleanup(writeRequest);
        return success;
    }

    protected abstract boolean writeStorage(String fromNode, String key, WriteRequest writeRequest, StorageMetadata metadata) throws IOException;

}
