/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.storage;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import bubble.service.cloud.StorageStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public abstract class NotificationHandler_storage_driver extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected CloudServiceDAO cloudDAO;
    @Autowired protected StorageStreamService storageStreamService;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("handleNotification: sender not found: "+n.getFromNode());

        final BubbleNode thisNode = configuration.getThisNode();
        final StorageDriverNotification notification = json(n.getPayloadJson(), StorageDriverNotification.class);
        final CloudService storage = cloudDAO.findByAccountAndId(thisNode.getAccount(), notification.getStorageService());
        if (storage == null) die("handleNotification: storage not found: "+notification.getStorageService());
        handleNotification(n, sender, notification, storage);
    }

    protected abstract void handleNotification(ReceivedNotification n,
                                               BubbleNode sender,
                                               StorageDriverNotification notification,
                                               CloudService storage);

}
