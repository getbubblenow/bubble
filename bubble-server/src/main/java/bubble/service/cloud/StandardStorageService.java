/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.storage.StorageServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.string.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class StandardStorageService implements StorageService {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    private class StorageTarget {
        public StorageServiceDriver storage;
        public String path;

        public StorageTarget(String account, String uri) {
            final String cloud = StorageServiceDriver.getCloud(uri);
            final CloudService cloudService = cloudDAO.findByAccountAndId(account, cloud);
            if (cloudService == null) throw notFoundEx(cloud);

            storage = cloudService.getStorageDriver(configuration);
            path = StorageServiceDriver.getPath(uri);
        }
    }

    private String thisNodeId() { return configuration.getThisNode() != null ? configuration.getThisNode().getUuid() : null; }

    public boolean exists(String account, String uri) {
        final StorageTarget target = new StorageTarget(account, uri);
        return target.storage.exists(thisNodeId(), target.path);
    }

    public StorageMetadata readMetadata(String account, String uri) {
        final StorageTarget target = new StorageTarget(account, uri);
        return target.storage.readMetadata(thisNodeId(), target.path);
    }

    public InputStream read(String account, String uri) {
        final StorageTarget target = new StorageTarget(account, uri);
        return target.storage.read(thisNodeId(), target.path);
    }

    public byte[] readFully(String account, String uri) {
        final StorageTarget target = new StorageTarget(account, uri);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            @Cleanup final InputStream in = target.storage.read(thisNodeId(), target.path);
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            return die("readFully: "+e);
        }
        return out.toByteArray();
    }

    public String readFullyBase64(String account, String uri) {
        return Base64.encodeBytes(readFully(account, uri));
    }

    public String readString(String account, String uri) {
        return new String(readFully(account, uri));
    }

    public boolean write(String account, String uri, InputStream data) {
        return write(account, uri, data, null);
    }

    public boolean write(String account, String uri, InputStream data, StorageMetadata metadata) {
        final StorageTarget target = new StorageTarget(account, uri);
        return target.storage.write(thisNodeId(), target.path, data, metadata);
    }

    public boolean write(String account, String uri, byte[] bytes) {
        return write(account, uri, new ByteArrayInputStream(bytes));
    }
    public boolean write(String account, String uri, byte[] bytes, StorageMetadata metadata) {
        return write(account, uri, new ByteArrayInputStream(bytes), metadata);
    }

    public boolean writeBase64(String account, String uri, String value) {
        return writeBase64(account, uri, value, null);
    }
    public boolean writeBase64(String account, String uri, String value, StorageMetadata metadata) {
        try {
            return write(account, uri, Base64.decode(value), metadata);
        } catch (IOException e) {
            return die("writeBase64: "+e);
        }
    }

    @Override public void delete(String account, String uri) throws IOException {
        final StorageTarget target = new StorageTarget(account, uri);
        target.storage.delete(thisNodeId(), target.path);
    }

}
