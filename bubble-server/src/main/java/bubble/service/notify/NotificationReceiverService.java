/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.notify;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.model.cloud.notify.SentNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.RestoreService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.security.RsaMessage;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static bubble.ApiConstants.MAX_NOTIFY_LOG;
import static bubble.model.cloud.BubbleNodeKey.TOKEN_GENERATION_LIMIT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.configuredIpsAndExternalIp;
import static org.cobbzilla.util.network.NetworkUtil.isLocalHost;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.resources.ResourceUtil.forbiddenEx;

@Service @Slf4j
public class NotificationReceiverService {

    @Autowired private ReceivedNotificationDAO receivedNotificationDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private RestoreService restoreService;

    public NotificationReceipt receive (InboundNotification n) {

        final RsaMessage message = n.getMessage();
        final String remoteHost = n.getRemoteHost();
        final String fromNodeUuid = n.getFromNodeUuid();
        final String fromKeyUuid = n.getFromKeyUuid();
        final String toKeyUuid = n.getToKeyUuid();
        final String restoreKey = n.getRestoreKey();

        final BubbleNode fromNode = nodeDAO.findByUuid(fromNodeUuid);
        if (fromNode == null) {
            log.warn("receiveNotification: fromNode not found: "+fromNodeUuid);
            throw forbiddenEx();
        }

        final BubbleNodeKey fromKey = findFromKey(fromNode, fromKeyUuid, remoteHost, restoreKey, message);

        // Find our key as receiver
        final BubbleNode thisNode = configuration.getThisNode();
        final BubbleNodeKey toKey = nodeKeyDAO.findByNodeAndUuid(thisNode.getUuid(), toKeyUuid);
        if (toKey == null) {
            log.warn("receiveNotification: node key " + toKeyUuid + " not found");
            throw forbiddenEx();
        }

        // If the message is not from ourselves, check the remoteHost
        if (!toKeyUuid.equals(fromKeyUuid) && !remoteHost.equals(fromKey.getRemoteHost())) {
            log.warn("receiveNotification: remoteHost mismatch: request="+remoteHost+", key="+fromKey.getRemoteHost());
            throw forbiddenEx();
        }

        // Decrypt message
        try {
            log.debug("decrypting message with key: "+toKey.getUuid());
            final String json = toKey.decrypt(message, fromKey.getRsaKey());
            if (log.isDebugEnabled()) {
                log.debug("_notify:\n<<<<< DECRYPTED NOTIFICATION <<<<<\n"
                        + truncate(json, MAX_NOTIFY_LOG)
                        + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            final SentNotification notification = json(json, SentNotification.class);
            return receive(fromNodeUuid, notification);

        } catch (Exception e) {
            return die("receiveNotification: "+e, e);
        }
    }

    public NotificationReceipt receive(String fromNodeUuid, SentNotification notification) {
        final NotificationReceipt receipt = new NotificationReceipt();
        if (notification.isResolveNodes()) {
            receipt.setResolvedSender(nodeDAO.findByUuid(fromNodeUuid));
            receipt.setResolvedRecipient(configuration.getThisNode());
        }

        receivedNotificationDAO.create(new ReceivedNotification(notification).setReceipt(receipt));
        notificationService.checkInbox();
        return receipt;
    }

    @Getter(lazy=true) private final Set<String> localIps = configuredIpsAndExternalIp();

    private BubbleNodeKey findFromKey(BubbleNode fromNode, String fromKeyUuid, String remoteHost, String restoreKey, RsaMessage message) {
        final String fromNodeUuid = fromNode.getUuid();
        BubbleNodeKey fromKey = nodeKeyDAO.findByNodeAndUuid(fromNodeUuid, fromKeyUuid);
        if (fromKey != null) {
            if (!fromKey.getRemoteHost().equals(remoteHost)) {
                // if request is from 127.0.0.1, check to see if fromKey is for a local address
                if (isLocalHost(remoteHost) && getLocalIps().contains(fromKey.getRemoteHost())) {
                    log.debug("findFromKey: request from 127.0.0.1 is OK, key is local: "+fromKey.getRemoteHost()+ " (ips="+ StringUtil.toString(getLocalIps())+")");
                } else {
                    log.warn("findFromKey: remoteHost for for node " + fromNodeUuid + " (key=" + fromKeyUuid + ", remoteHost=" + fromKey.getRemoteHost() + ") does not match request: " + remoteHost+ " (ips="+ StringUtil.toString(getLocalIps())+")");
                    throw forbiddenEx();
                }
            }
            return fromKey;
        }

        // Do we have any other keys for this node?
        final List<BubbleNodeKey> currentKeys = nodeKeyDAO.findByNode(fromNodeUuid);

        // Ensure remote host matches
        final String currentRemoteHost = nodeKeyDAO.findRemoteHostForNode(fromNodeUuid);
        if (currentRemoteHost != null && !remoteHost.equals(currentRemoteHost)) {
            log.warn("findFromKey: new key provided for node "+fromNodeUuid+" but remoteHost does not match: "+remoteHost);
            throw forbiddenEx();
        }

        // Do we have the current key?
        fromKey = currentKeys.stream()
                .filter(k -> k.getPublicKey().equals(message.getPublicKey()))
                .findFirst()
                .orElse(null);
        if (fromKey != null) return fromKey;

        // Create a record for this key, no private key because only the node knows that.
        // Record remoteHost, future requests must match
        if (currentKeys.isEmpty()) {
            // verify old keys match remoteHost
            fromKey = createFromKey(fromNode, fromKeyUuid, remoteHost, message);
            log.info("findFromKey: registered new node key: " + fromKeyUuid + " for node: " + fromNodeUuid);
            return fromKey;
        }

        // we have current keys, why are they not using one of those?
        // maybe because they are all about to expire?
        if (currentKeys.stream().allMatch(k -> k.expiresInLessThan(TOKEN_GENERATION_LIMIT))) {
            // OK, we'll create it since all other keys have less than 24 hours left
            fromKey = createFromKey(fromNode, fromKeyUuid, remoteHost, message);
            log.info("findFromKey: due to expiring current key, registered new node key: " + fromKeyUuid + " for node: " + fromNodeUuid);
            return fromKey;

        } else if (!empty(restoreKey) && isValidRestoreKey(fromNode, restoreKey, remoteHost)) {
            fromKey = createFromKey(fromNode, fromKeyUuid, remoteHost, message);
            log.info("findFromKey: accepting key with valid restoreKey ("+restoreKey+"): registered new node key: " + fromKeyUuid + " for node: " + fromNodeUuid);
            return fromKey;

        } else {
            // todo: send verify_key synchronous message to node, if it can verify the key, then we'll accept it
            log.warn("findFromKey: new key not accepted, current keys exist that are not expiring soon, node should use one of those");
            throw forbiddenEx();
        }
    }

    private boolean isValidRestoreKey(BubbleNode fromNode, String restoreKey, String remoteHost) {
        final BubbleNetwork network = networkDAO.findByUuid(fromNode.getNetwork());
        if (network == null) {
            log.info("isValidRestoreKey: network not found ("+fromNode.getNetwork()+"), returning false");
            return false;
        }
        if (network.getState() != BubbleNetworkState.restoring) {
            log.info("isValidRestoreKey: network ("+network.getUuid()+") is not in 'restoring' state ("+network.getState()+"), returning false");
            return false;
        }
        if (network.getMtimeAge() > RestoreService.RESTORE_WINDOW) {
            log.info("isValidRestoreKey: network ("+network.getUuid()+") has been in 'restoring' state too long ("+formatDuration(network.getMtimeAge())+"), must stop network and retry restore, returning false");
            return false;
        }
        if (!restoreService.isValidRestoreKey(restoreKey)) {
            log.info("isValidRestoreKey: restoreKey ("+restoreKey+") is not valid, returning false");
            return false;
        }
        if (!fromNode.hasSameIp(remoteHost)) {
            log.info("isValidRestoreKey: remoteHost ("+remoteHost+") does not match IP of restoring node ("+fromNode.id()+"), returning false");
            return false;
        }
        return true;
    }

    private final Object createKeyLock = new Object();

    private BubbleNodeKey createFromKey(BubbleNode fromNode, String fromKeyUuid, String remoteHost, RsaMessage message) {
        synchronized (createKeyLock) {
            final BubbleNodeKey existing = nodeKeyDAO.findByUuid(fromKeyUuid);
            return existing != null
                    ? existing
                    : nodeKeyDAO.create(new BubbleNodeKey(fromKeyUuid, fromNode, message.getPublicKey(), remoteHost));
        }
    }

}
