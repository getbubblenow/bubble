/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.notify;

import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.dao.cloud.notify.SentNotificationDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.*;
import bubble.notify.SynchronousNotification;
import bubble.notify.SynchronousNotificationReply;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ConnectTimeoutException;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.api.ApiException;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.util.RestResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.MAX_NOTIFY_LOG;
import static bubble.ApiConstants.NOTIFY_ENDPOINT;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.util.system.Sleep.sleep;

@Service @Slf4j
public class NotificationService {

    public static final long SYNC_TIMEOUT = MINUTES.toMillis(10);
    public static final long SYNC_WAIT_TIME = SECONDS.toMillis(2);

    @Autowired private SentNotificationDAO sentNotificationDAO;
    @Autowired private ReceivedNotificationDAO receivedNotificationDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private NotificationReceiverService notificationReceiverService;

    public NotificationReceipt notify(ApiClientBase api, NotificationType type, Object payload) {
        return notify(api, null, type, payload);
    }

    public NotificationReceipt notify(BubbleNode node, NotificationType type, Object payload) {
        if (nodeKeyDAO.findFirstByNode(node.getUuid()) == null) {
            log.warn("notify: no keys found for node: "+node.getUuid());
            return null;
        }
        @Cleanup final ApiClientBase api = type == NotificationType.health_check
                ? node.getApiQuickClient(configuration)
                : node.getApiClient(configuration);
        final String toNodeUuid = node.getUuid();
        return notify(api, toNodeUuid, type, payload);
    }

    public NotificationReceipt notifyWithEnhancedReceipt(BubbleNode node, NotificationType type, Object payload) {
        final ApiClientBase api = node.getApiClient(configuration);
        final String toNodeUuid = node.getUuid();
        return notifyWithEnhancedReceipt(api, toNodeUuid, type, payload);
    }

    public NotificationReceipt notify(ApiClientBase api, String toNodeUuid, NotificationType type, Object payload) {
        return _notify(api, toNodeUuid, type, payload, false);
    }
    public NotificationReceipt notifyWithEnhancedReceipt(ApiClientBase api, String toNodeUuid, NotificationType type, Object payload) {
        return _notify(api, toNodeUuid, type, payload, true);
    }
    public NotificationReceipt _notify(ApiClientBase api, String toNodeUuid, NotificationType type, Object payload, boolean enhancedReceipt) {
        final BubbleNode thisNode = configuration.getThisNode();
        final boolean isLocal = toNodeUuid != null && toNodeUuid.equals(thisNode.getUuid());
        final SentNotification notification = sentNotificationDAO.create((SentNotification) new SentNotification()
                .setNotificationId(getNotificationId(payload))
                .setAccount(thisNode.getAccount())
                .setType(type)
                .setFromNode(thisNode.getUuid())
                .setToNode(toNodeUuid != null ? toNodeUuid : api.getBaseUri())
                .setUri(api.getBaseUri() + NOTIFY_ENDPOINT)
                .setPayloadJson(payload == null ? null : json(payload)));

        if (isLocal && configuration.localNotificationStrategy() == LocalNotificationStrategy.inline) {
            final NotificationReceipt receipt = new NotificationReceipt();
            final ReceivedNotification n = new ReceivedNotification(notification).setReceipt(receipt);
            NotificationInboxProcessor.processNotification(n, syncRequests, configuration);
            return receipt;

        } else {
            notification.setStatus(NotificationSendStatus.sending);
            sentNotificationDAO.update(notification);

            try {
                final NotificationReceipt receipt;
                if (isLocal && configuration.localNotificationStrategy() == LocalNotificationStrategy.queue) {
                    log.info("_notify: >>>>> SENDING " + notification.getType() + " to SELF via NotificationReceiverService >>>>>");
                    receipt = notificationReceiverService.receive(thisNode.getUuid(), notification);

                } else {
                    final String json = json(notification);
                    if (log.isTraceEnabled()) {
                        log.trace("_notify:\n>>>>> SENDING to " + api.getConnectionInfo().getBaseUri() + " >>>>>\n"
                                + truncate(json, MAX_NOTIFY_LOG)
                                + "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                    } else {
                        log.info("_notify: >>>>> SENDING " + notification.getType() + " to " + api.getConnectionInfo().getBaseUri() + " >>>>>");
                    }
                    final RestResponse response = api.doPost(NOTIFY_ENDPOINT, json);
                    receipt = json(response.json, NotificationReceipt.class);
                }
                notification.setStatus(NotificationSendStatus.sent);
                notification.setReceipt(receipt);
                sentNotificationDAO.update(notification);
                log.debug("_notify: <<<<< RECEIPT <<<<<< " + json(receipt, COMPACT_MAPPER) + " <<<<<<<<<<<<<<<<<<");
                return receipt;

            } catch (ConnectException | ConnectTimeoutException | UnknownHostException | ApiException e) {
                notification.setStatus(NotificationSendStatus.error);
                notification.setException(e);
                sentNotificationDAO.update(notification);
                throw new IllegalStateException("_notify: "+shortError(e), e);

            } catch (Exception e) {
                notification.setStatus(NotificationSendStatus.error);
                notification.setException(e);
                sentNotificationDAO.update(notification);
                return die("_notify: "+shortError(e), e);
            }
        }
    }

    public String getNotificationId(Object payload) {
        if (payload instanceof SynchronousNotification) return ((SynchronousNotification) payload).getId();
        if (payload instanceof SynchronousNotificationReply) return ((SynchronousNotificationReply) payload).getNotificationId();
        return null;
    }

    private final Map<String, SynchronousNotification> syncRequests = new ExpirationMap<>(SYNC_TIMEOUT + SECONDS.toMillis(5));
    private final Map<String, SynchronousNotification> syncRequestCache = new ExpirationMap<>(SECONDS.toMillis(15));

    public <T> T notifySync(BubbleNode delegate, NotificationType type, SynchronousNotification notification) {
        final String cacheKey = notification.getCacheKey(delegate, type);
        SynchronousNotification activeNotification;
        synchronized (syncRequestCache) {
            activeNotification = syncRequestCache.get(cacheKey);
            if (activeNotification == null || !type.canReturnCachedResponse()) {
                // no one else is calling, we are the activeNotification
                syncRequestCache.put(cacheKey, notification);
                activeNotification = notification;
            }
        }
        if (activeNotification == notification) {
            // register callback for response
            log.debug("notifySync ("+notification.getClass().getSimpleName()+"/"+notification.getId()+"): sending notification: "+activeNotification.getId());
            syncRequests.put(notification.getId(), notification);
            final NotificationReceipt receipt = _notify(
                    delegate.getApiClient(configuration),
                    delegate.getUuid(),
                    type,
                    notification,
                    false);
        } else {
            log.debug("notifySync ("+notification.getClass().getSimpleName()+"/"+notification.getId()+"): waiting for identical notification: "+activeNotification.getId());
        }
        final long start = now();
        while (now() - start < SYNC_TIMEOUT) {
            if (activeNotification.hasException()) {
                return die(activeNotification.getException().toString());
            } else if (activeNotification.hasResponse()) {
                return type.toResponse(activeNotification.getResponse());
            }
            sleep(SYNC_WAIT_TIME, "awaiting sync notification response: "+notification.getClass().getSimpleName());
        }
        return die("notifySync ("+notification.getClass().getSimpleName()+"/"+notification.getId()+"): timeout");
    }

    private final Object inboxLock = new Object();

    public void checkInbox() {
        log.debug("checkInbox: starting");
        try {
            synchronized (inboxLock) {
                final List<ReceivedNotification> toProcess = receivedNotificationDAO.findNewReceived();
                log.debug("checkInbox: found "+toProcess.size()+" new messages");
                for (final ReceivedNotification n : toProcess) {
                    n.setProcessingStatus(NotificationProcessingStatus.processing);
                    receivedNotificationDAO.update(n);
                    receivedNotificationDAO.flush();
                    log.debug("checkInbox: spawning NotificationInboxProcessor for "+n.getType()+" notificationId="+n.getNotificationId());
                    daemon(new NotificationInboxProcessor(n, syncRequests, configuration, receivedNotificationDAO));
                }
            }
            log.debug("checkInbox: finished");
        } catch (Exception e) {
            log.error("checkInbox: "+e, e);
        }
    }

    public static boolean validPeer(BubbleNode thisNode, BubbleNode peer) {
        if (peer == null || !peer.hasUuid()) return false;
        if (peer.getNetwork() == null || peer.getDomain() == null || peer.getAccount() == null) return false;
        if (!peer.getNetwork().equals(thisNode.getNetwork())) {
            log.warn("Peer is not in our network: "+peer.getUuid());
            return false;
        }
        if (!peer.getDomain().equals(thisNode.getDomain())) {
            log.warn("Peer is not in our domain: "+peer.getUuid());
            return false;
        }
        if (!peer.getAccount().equals(thisNode.getAccount())) {
            log.warn("Peer is not ours: "+peer.getUuid());
            return false;
        }
        return true;
    }

}
