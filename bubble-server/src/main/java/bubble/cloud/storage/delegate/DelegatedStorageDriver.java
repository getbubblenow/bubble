/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.storage.delegate;

import bubble.cloud.DelegatedStorageDriverBase;
import bubble.cloud.storage.StorageServiceDriver;
import bubble.cloud.storage.WriteRequest;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.model.cloud.notify.NotificationType;
import bubble.notify.storage.StorageDriverNotification;
import bubble.notify.storage.StorageListing;
import bubble.notify.storage.StorageResult;
import bubble.notify.storage.StorageStreamRequest;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.wizard.client.ApiClientBase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static bubble.ApiConstants.EP_READ;
import static bubble.ApiConstants.NOTIFY_ENDPOINT;
import static bubble.model.cloud.notify.NotificationType.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class DelegatedStorageDriver extends DelegatedStorageDriverBase {

    public DelegatedStorageDriver(CloudService cloud) { super(cloud); }

    @Override public boolean _exists(String fromNode, String key) {
        return booleanRequest(key, storage_driver_exists);
    }

    @Override public StorageMetadata readMetadata(String fromNode, String key) {
        final BubbleNode delegate = getDelegateNode();
        final StorageResult result = notificationService.notifySync(delegate, storage_driver_read_metadata, notification(new StorageDriverNotification(key)));
        return result.success() ? result.getData(StorageMetadata.class) : die("readMetadata: remote error: "+result.getError());
    }

    @Override public InputStream _read(String fromNode, String key) {
        final BubbleNode delegate = getDelegateNode();
        final String token = notificationService.notifySync(delegate, storage_driver_read, notification(new StorageDriverNotification(key)));
        try {
            @Cleanup final ApiClientBase downloadClient = delegate.getDownloadClient(configuration);
            return HttpUtil.get(downloadClient.getBaseUri()+NOTIFY_ENDPOINT+EP_READ+"/"+token);
        } catch (IOException e) {
            return die("_read: "+e);
        }
    }

    @Override public boolean writeStorage(String fromNode, String key, WriteRequest writeRequest, StorageMetadata metadata) {
        log.info("writeStorage: key="+key);
        final BubbleNode delegate = getDelegateNode();
        final StorageDriverNotification notification = notification(new StorageDriverNotification(key, metadata));

        // tell the delegate to READ from our writeRequest, and write that to the delegated storage
        final String token = storageStreamService.registerRead(new StorageStreamRequest()
                .setCloud(cloud.getUuid())
                .setFromNode(delegate.getUuid())
                .setKey(key), writeRequest);
        notification.setToken(token);

        log.info("writeStorage: (key="+key+"): sending storage_driver_write with token: "+token);
        final StorageResult result = notificationService.notifySync(delegate, storage_driver_write, notification);
        return result.success();
    }

    @Override public boolean canWrite(String fromNode, String toNode, String key) {
        // Only allows writes from nodes in same network as self
        final BubbleNode to = nodeDAO.findByUuid(toNode);
        return to != null && to.getNetwork().equals(configuration.getThisNetwork().getUuid());
    }

    @Override public boolean delete(String fromNode, String key) {
        return booleanRequest(key, storage_driver_delete);
    }

    @Override public boolean deleteNetwork(String networkUuid) throws IOException {
        return booleanRequest(networkUuid, storage_driver_delete_network);
    }

    @Override public StorageListing list(String fromNode, String prefix) throws IOException {
        final BubbleNode delegate = getDelegateNode();
        final StorageResult result = notificationService.notifySync(delegate, storage_driver_list, notification(new StorageDriverNotification(prefix)));
        return result.success() ? result.getData(StorageListing.class) : die("list: remote error: "+result.getError());
    }

    @Override public StorageListing listNext(String fromNode, String listingId) throws IOException {
        final BubbleNode delegate = getDelegateNode();
        final StorageResult result = notificationService.notifySync(delegate, storage_driver_list_next, notification(new StorageDriverNotification(listingId)));
        return result.success() ? result.getData(StorageListing.class) : die("listNext: remote error: "+result.getError());
    }

    @Override public boolean rekey(String fromNode, CloudService newStorage) throws IOException {
        StorageListing listing = list(fromNode, "");
        final StorageServiceDriver newDriver = newStorage.getStorageDriver(configuration);
        while (true) {
            Arrays.stream(listing.getKeys()).forEach(k -> {
                final StorageMetadata metadata = readMetadata(fromNode, k);
                final InputStream in = read(fromNode, k);
                newDriver.write(fromNode, k, in, metadata.setNonce(null));
            });
            if (!listing.isTruncated()) break;
            listing = listNext(fromNode, listing.getListingId());
        }
        return false; // booleanRequest(fromNode, json(newStorage), storage_driver_rekey);
    }

    private boolean booleanRequest(String key, NotificationType type) {
        final BubbleNode delegate = getDelegateNode();
        final StorageResult result = notificationService.notifySync(delegate, type, notification(new StorageDriverNotification(key)));
        return result.success() ? result.getData().booleanValue() : die("remote error: "+result.getError());
    }

    private StorageDriverNotification notification(StorageDriverNotification n) { return n.setStorageService(cloud.getDelegated()); }

}
