package bubble.resources.notify;

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
import bubble.notify.storage.StorageStreamRequest;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.RestoreService;
import bubble.service.cloud.StorageStreamService;
import bubble.service.notify.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.security.RsaMessage;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static bubble.ApiConstants.*;
import static bubble.client.BubbleNodeClient.*;
import static bubble.model.cloud.BubbleNodeKey.TOKEN_GENERATION_LIMIT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.configuredIpsAndExternalIp;
import static org.cobbzilla.util.network.NetworkUtil.isLocalHost;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(NOTIFY_ENDPOINT)
@Service @Slf4j
public class InboundNotifyResource {

    @Autowired private ReceivedNotificationDAO receivedNotificationDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private StorageStreamService storageStreamService;
    @Autowired private RestoreService restoreService;

    @Getter(lazy=true) private final Set<String> localIps = configuredIpsAndExternalIp();

    @POST
    public Response receiveNotification(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        JsonNode jsonNode) {
        try {
            log.debug("_notify:\n<<<<< RECEIVED NOTIFICATION from "+getRemoteHost(req)+" <<<<<\n"
                    + (jsonNode == null ? "null" : truncate(json(jsonNode), MAX_NOTIFY_LOG))
                    + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            final RsaMessage message = json(jsonNode, RsaMessage.class);

            final String remoteHost = getRemoteHost(req);
            if (empty(remoteHost)) {
                log.warn("receiveNotification: remoteHost was empty, forbidden");
                return forbidden();  // who are you?
            }

            // Find key from sender
            final String fromKeyUuid = req.getHeader(H_BUBBLE_FROM_NODE_KEY);
            if (fromKeyUuid == null) {
                log.warn("receiveNotification: missing " + H_BUBBLE_FROM_NODE_KEY + " request header");
                return forbidden();
            }
            final String fromNodeUuid = req.getHeader(H_BUBBLE_FROM_NODE_UUID);
            if (fromNodeUuid == null) {
                log.warn("receiveNotification: missing " + H_BUBBLE_FROM_NODE_UUID + " request header");
                return forbidden();
            }
            final BubbleNode fromNode = nodeDAO.findByUuid(fromNodeUuid);
            if (fromNode == null) {
                log.warn("receiveNotification: fromNode not found: "+fromNodeUuid);
                return forbidden();
            }

            final String restoreKey = req.getHeader(H_BUBBLE_RESTORE_KEY);
            log.debug("receiveNotification: header value for "+H_BUBBLE_RESTORE_KEY+"="+restoreKey);
            final BubbleNodeKey fromKey = findFromKey(fromNode, fromKeyUuid, remoteHost, restoreKey, message);

            // Find our key as receiver
            final String toKeyUuid = req.getHeader(H_BUBBLE_TO_NODE_KEY);
            if (toKeyUuid == null) {
                log.warn("receiveNotification: missing " + H_BUBBLE_TO_NODE_KEY + " request header");
                return forbidden();
            }
            final BubbleNode thisNode = configuration.getThisNode();
            final BubbleNodeKey toKey = nodeKeyDAO.findByNodeAndUuid(thisNode.getUuid(), toKeyUuid);
            if (toKey == null) {
                log.warn("receiveNotification: node key " + toKeyUuid + " not found");
                return forbidden();
            }

            // If the message is not from ourselves, check the remoteHost
            if (!toKeyUuid.equals(fromKeyUuid) && !remoteHost.equals(fromKey.getRemoteHost())) {
                log.warn("receiveNotification: remoteHost mismatch: request="+remoteHost+", key="+fromKey.getRemoteHost());
                return forbidden();
            }

            // Decrypt message
            log.debug("decrypting message with key: "+toKey.getUuid());
            final String json = toKey.decrypt(message, fromKey.getRsaKey());
            log.debug("_notify:\n<<<<< DECRYPTED NOTIFICATION <<<<<\n"
                    + truncate(json, MAX_NOTIFY_LOG)
                    + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            final SentNotification notification = json(json, SentNotification.class);

            final NotificationReceipt receipt = new NotificationReceipt();
            if (notification.isResolveNodes()) {
                receipt.setResolvedSender(nodeDAO.findByUuid(fromNodeUuid));
                receipt.setResolvedRecipient(configuration.getThisNode());
            }

            receivedNotificationDAO.create(new ReceivedNotification(notification).setReceipt(receipt));
            notificationService.checkInbox();

            return ok(receipt);

        } catch (Exception e) {
            log.error("receiveNotification: "+e, e);
            return serverError();
        }
    }

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

    @GET @Path(EP_READ+"/{token}")
    public Response readStorage(@Context Request req,
                                @Context ContainerRequest ctx,
                                @PathParam("token") String token) {
        log.debug("readStorage: token="+token);
        final StorageStreamRequest storageRequest = storageStreamService.findRead(token);
        if (storageRequest == null) {
            log.error("readStorage ("+token+"): token not found in storageStreamService");
            return notFound(token);
        }

        // ensure fromNode matches request
        final BubbleNode fromNode = nodeDAO.findByUuid(storageRequest.getFromNode());
        if (fromNode == null) {
            log.error("readStorage ("+token+"): fromNode not found");
            return notFound(storageRequest.getFromNode());
        }

        final String remoteHost = getRemoteHost(req);
        if (!fromNode.hasSameIp(remoteHost)) {
            if (getLocalIps().contains(fromNode.getIp4()) || getLocalIps().contains(fromNode.getIp6())) {
                log.debug("readStorage: local request, allowed");
            } else {
                log.error("readStorage (" + token + "): fromNode (" + fromNode.id() + ") does not match remoteHost: " + remoteHost);
                return notFound(storageRequest.getFromNode());
            }
        }

        try {
            log.debug("readStorage: token is valid ("+token+"): finding stream");
            final InputStream data = storageStreamService.read(storageRequest);
            return data != null
                    ? stream(APPLICATION_OCTET_STREAM, data)
                    : notFound(storageRequest.getKey());
        } catch (Exception e) {
            return die("readStorage: "+e);
        } finally {
            storageStreamService.clearToken(token);
        }
    }

}
