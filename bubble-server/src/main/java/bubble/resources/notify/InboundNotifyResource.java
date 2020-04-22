/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.notify;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.notify.storage.StorageStreamRequest;
import bubble.service.cloud.StorageStreamService;
import bubble.service.notify.InboundNotification;
import bubble.service.notify.NotificationReceiverService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.security.RsaMessage;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Set;

import static bubble.ApiConstants.*;
import static bubble.client.BubbleNodeClient.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.configuredIpsAndExternalIp;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(NOTIFY_ENDPOINT)
@Service @Slf4j
public class InboundNotifyResource {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private StorageStreamService storageStreamService;
    @Autowired private NotificationReceiverService notificationReceiverService;

    @Getter(lazy=true) private final Set<String> localIps = configuredIpsAndExternalIp();

    @POST
    public Response receiveNotification(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        JsonNode jsonNode) {
        try {
            if (log.isTraceEnabled()) {
                log.trace("_notify:\n<<<<< RECEIVED NOTIFICATION from " + getRemoteHost(req) + " <<<<<\n"
                        + (jsonNode == null ? "null" : truncate(json(jsonNode), MAX_NOTIFY_LOG))
                        + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
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

            // Find our key as receiver
            final String toKeyUuid = req.getHeader(H_BUBBLE_TO_NODE_KEY);
            if (toKeyUuid == null) {
                log.warn("receiveNotification: missing " + H_BUBBLE_TO_NODE_KEY + " request header");
                return forbidden();
            }

            final NotificationReceipt receipt = notificationReceiverService.receive(new InboundNotification()
                    .setMessage(message)
                    .setRemoteHost(remoteHost)
                    .setFromNodeUuid(fromNodeUuid)
                    .setFromKeyUuid(fromKeyUuid)
                    .setToKeyUuid(toKeyUuid)
                    .setRestoreKey(restoreKey)
            );
            return ok(receipt);

        } catch (Exception e) {
            log.error("receiveNotification: "+e, e);
            return serverError();
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
