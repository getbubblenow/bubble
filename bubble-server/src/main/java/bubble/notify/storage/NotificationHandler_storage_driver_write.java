/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.storage;

import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.wizard.client.ApiClientBase;

import java.io.IOException;
import java.io.InputStream;

import static bubble.ApiConstants.EP_READ;
import static bubble.ApiConstants.NOTIFY_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class NotificationHandler_storage_driver_write extends NotificationHandler_storage_driver_result<Boolean> {

    @Override protected Boolean handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) throws IOException {
        @Cleanup final InputStream in = loadWrite(n, notification);
        log.info("handle: loaded input stream: "+in.getClass().getName());
        return storage.getStorageDriver(configuration).write(n.getFromNode(), notification.getKey(), in, notification.getMetadata());
    }

    private InputStream loadWrite(ReceivedNotification n, StorageDriverNotification notification) {
        final BubbleNode fromNode = nodeDAO.findByUuid(n.getFromNode());
        if (fromNode == null) return die("loadWrite: fromNode not found: "+n.getUuid());
        final ApiClientBase nodeClient = fromNode.getApiClient(configuration);
        try {
            final String readUri = NOTIFY_ENDPOINT + EP_READ + "/" + notification.getToken();
            log.info("loadWrite: reading from "+readUri);
            return nodeClient.getStream(new HttpRequestBean().setUri(readUri));
        } catch (Exception e) {
            return die("loadWrite: error reading from node "+fromNode.id()+": "+e);
        }
    }

    @Override protected JsonNode toData(Boolean returnVal) { return BooleanNode.valueOf(returnVal); }

}
