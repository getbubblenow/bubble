/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.storage;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.notify.storage.StorageListing;
import lombok.Cleanup;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.error.ExceptionHandler;
import org.cobbzilla.util.string.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.toStringOrDie;
import static org.cobbzilla.util.system.Sleep.sleep;

public interface StorageServiceDriver extends CloudServiceDriver {

    String STORAGE_PREFIX = "storage://";
    String STORAGE_PREFIX_TRUNCATED = "storage:/";

    static String getCloud(String path) {
        if (!path.startsWith(STORAGE_PREFIX)) {
            return die("getCloud: no cloud in path: "+path);
        }
        try {
            int slashPos = path.indexOf('/', STORAGE_PREFIX.length() + 1);
            if (slashPos == -1) return die("getCloud: invalid path: "+path);
            return path.substring(STORAGE_PREFIX.length(), slashPos);
        } catch (Exception e) {
            return die("getCloud: error: "+e);
        }
    }
    static String getPath(String path) {
        if (path.startsWith(STORAGE_PREFIX)) {
            try {
                int slashPos = path.indexOf('/', STORAGE_PREFIX.length() + 1);
                if (slashPos == -1) return die("getPath: invalid path: "+path);
                return path.substring(slashPos + 1);
            } catch (Exception e) {
                return die("getPath: error: "+e);
            }
        }
        return path;
    }
    static String getUri(String cloud, String path) {
        return STORAGE_PREFIX + cloud + (path.startsWith("/") ? "" : "/") + path;
    }

    default CloudServiceType getType() { return CloudServiceType.storage; }

    Logger log = LoggerFactory.getLogger(StorageServiceDriver.class);

    default ExceptionHandler getExceptionRunnable() { return ExceptionHandler.exceptionRunnable(getFatalExceptionClasses()); }
    default Class[] getFatalExceptionClasses() { return new Class[0]; }
    default int getMaxTries() { return 5; }

    default boolean exists(String fromNode, String key) {
        // DON'T use a lambda here, it will capture too much crap and cause memory leaks
        Exception lastEx = null;
        for (int i=0; i<getMaxTries(); i++) {
            try {
                return _exists(fromNode, key);
            } catch (Exception e) {
                lastEx = e;
                sleep(DEFAULT_RETRY_BACKOFF.apply(i), "waiting to retry _exists");
            }
        }
        return die(lastEx == null ? "exists error, no lastEx" : "exists: "+lastEx);
    }

    boolean _exists(String fromNode, String key) throws IOException;

    StorageMetadata readMetadata(String fromNode, String key);

    InputStream _read(String fromNode, String key) throws IOException;

    default InputStream read(String fromNode, String key) {
        // DON'T use a lambda here, it will capture too much crap and cause memory leaks
        Exception lastEx = null;
        for (int i=0; i<getMaxTries(); i++) {
            try {
                return _read(fromNode, key);
            } catch (Exception e) {
                lastEx = e;
                sleep(DEFAULT_RETRY_BACKOFF.apply(i), "waiting to retry _read");
            }
        }
        return die(lastEx == null ? "read error, no lastEx" : "read: "+lastEx);
    }

    default byte[] readFully(String fromNode, String key) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            @Cleanup final InputStream in = read(fromNode, key);
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            return die("readFully: "+e);
        }
        return out.toByteArray();
    }

    default String readFullyBase64(String fromNode, String key) {
        return Base64.encodeBytes(readFully(fromNode, key));
    }

    default String readString(String fromNode, String key) {
        return new String(readFully(fromNode, key));
    }

    @Override default boolean test () {
        final String node = getTestNodeId();
        final String key = getTestKey();
        final String data = getTestData();
        return write(node, key, data.getBytes())
                && toStringOrDie(read(node, key)).equals(data)
                && delete(node, key);
    }

    default String getTestNodeId () { return ROOT_NETWORK_UUID; }
    default String getTestKey    () { return "driver_test_key_" +randomAlphanumeric(30); }
    default String getTestData   () { return "driver_test_data_"+randomAlphanumeric(100); }

    boolean _write(String fromNode, String key, InputStream data, StorageMetadata metadata, String requestId) throws IOException;

    default boolean write(String fromNode, String key, InputStream data) {
        // DON'T use a lambda here, it will capture too much crap and cause memory leaks
        final String requestId = randomUUID().toString();
        Exception lastEx = null;
        for (int i=0; i<getMaxTries(); i++) {
            try {
                return _write(fromNode, key, data, null, requestId);
            } catch (Exception e) {
                lastEx = e;
                sleep(DEFAULT_RETRY_BACKOFF.apply(i), "waiting to retry _write");
            }
        }
        return die(lastEx == null ? "write error, no lastEx" : "write: "+shortError(lastEx));
    }

    default boolean write(String fromNode, String key, InputStream data, StorageMetadata metadata) {
        // DON'T use a lambda here, it will capture too much crap and cause memory leaks
        final String requestId = randomUUID().toString();
        Exception lastEx = null;
        for (int i=0; i<getMaxTries(); i++) {
            try {
                return _write(fromNode, key, data, metadata, requestId);
            } catch (Exception e) {
                lastEx = e;
                sleep(DEFAULT_RETRY_BACKOFF.apply(i), "waiting to retry _write");
            }
        }
        return die(lastEx == null ? "write error, no lastEx" : "write: "+lastEx);
    }

    default boolean write(String fromNode, String key, byte[] bytes) {
        return write(fromNode, key, new ByteArrayInputStream(bytes));
    }
    default boolean write(String fromNode, String key, byte[] bytes, StorageMetadata metadata) {
        return write(fromNode, key, new ByteArrayInputStream(bytes), metadata);
    }

    default boolean writeBase64(String fromNode, String key, String value) {
        return writeBase64(fromNode, key, value, null);
    }

    default boolean writeBase64(String fromNode, String key, String value, StorageMetadata metadata) {
        try {
            return write(fromNode, key, Base64.decode(value), metadata);
        } catch (IOException e) {
            return die("writeBase64: "+e);
        }
    }

    boolean canWrite(String fromNode, String toNode, String key);

    boolean delete(String fromNode, String uri);
    boolean deleteNetwork(String networkUuid) throws IOException;

    boolean rekey(String fromNode, CloudService newCloud) throws IOException;

    StorageListing list(String fromNode, String prefix) throws IOException;
    StorageListing listNext(String fromNode, String listingId) throws IOException;

}
