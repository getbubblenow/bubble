/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.vultr;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.*;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.system.CommandResult;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static bubble.model.cloud.BubbleNode.TAG_INSTANCE_ID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class VultrDriver extends ComputeServiceDriverBase {

    public static final String PARAM_API_KEY = "apiKey";
    public static final String API_KEY_HEADER = "API-Key";

    public static final String VULTR_API_BASE = "https://api.vultr.com/v1/";

    public static final String REGIONS_URL = VULTR_API_BASE + "regions/list";
    public static final String PLANS_URL = VULTR_API_BASE + "plans/list";
    public static final String OS_URL = VULTR_API_BASE + "os/list";
    public static final String SNAPSHOT_URL = VULTR_API_BASE + "snapshot/list";

    public static final String VULTR_SUBID = "SUBID";
    public static final String VULTR_V4_IP = "main_ip";
    public static final String VULTR_V6_IP = "v6_main_ip";
    public static final String VULTR_LABEL = "label";

    public static final String VULTR_STATUS = "status";
    public static final String VULTR_STATUS_PENDING = "pending";
    public static final String VULTR_STATUS_ACTIVE = "active";

    public static final String VULTR_SERVER_STATE = "server_state";
    public static final String VULTR_STATE_NONE = "none";
    public static final String VULTR_STATE_OK = "ok";
    public static final String VULTR_STATE_LOCKED = "locked";

    public static final String CREATE_SERVER_URL = VULTR_API_BASE + "server/create";
    public static final String DESTROY_SERVER_URL = VULTR_API_BASE + "server/destroy";
    public static final String LIST_SERVERS_URL = VULTR_API_BASE + "server/list";
    public static final String POLL_SERVER_URL = LIST_SERVERS_URL + "?" + VULTR_SUBID + "=";

    @Getter(lazy=true) private final List<CloudRegion> cloudRegions = loadCloudResources(REGIONS_URL, new VultrRegionParser());
    @Getter(lazy=true) private final List<ComputeNodeSize> cloudSizes = loadCloudResources(PLANS_URL, new VultrComputeNodeSizeParser());
    @Getter(lazy=true) private final List<OsImage> cloudOsImages = loadCloudResources(OS_URL, new VultrOsImageParser());

    @Getter(lazy=true) private final int snapshotOsId = initSnapshotOsId();
    private int initSnapshotOsId() {
        final OsImage snapshot = getCloudOsImages().stream()
                .filter(i -> i.getName().equals("Snapshot"))
                .findFirst()
                .orElse(null);
        return snapshot == null ? die("initSnapshotOsId: no snapshot OS found") : Integer.parseInt(snapshot.getId());
    }

    public static final long SERVER_START_INITIAL_INTERVAL = SECONDS.toMillis(30);
    public static final long SERVER_START_POLL_INTERVAL = SECONDS.toMillis(5);
    public static final long SERVER_START_TIMEOUT = MINUTES.toMillis(10);
    public static final long SERVER_STOP_TIMEOUT = MINUTES.toMillis(5);
    public static final long SERVER_STOP_CHECK_INTERVAL = SECONDS.toMillis(5);

    private <T> List<T> loadCloudResources(String uri, ResourceParser<T, List<T>> parser) {
        try {
            final HttpRequestBean request = auth(new HttpRequestBean(uri));
            final HttpResponseBean response = getResponse(request);
            final JsonNode node = json(response.getEntityString(), JsonNode.class);
            final List<T> resources = parser.newResults();
            for (Iterator<String> fields = node.fieldNames(); fields.hasNext(); ) {
                final String id = fields.next();
                final JsonNode item = node.get(id);
                final T obj = parser.parse(item);
                if (obj != null) resources.add(obj);
            }
            return resources;
        } catch (Exception e) {
            return die("loadCloudResources("+uri+"): "+e, e);
        }
    }

    @Override public void postSetup() {
        if (credentials != null && credentials.hasParam(PARAM_API_KEY) && credentials.getParam(PARAM_API_KEY).contains("{{")) {
            final String apiKey = configuration.applyHandlebars(credentials.getParam(PARAM_API_KEY));
            credentials.setParam(PARAM_API_KEY, apiKey);
        }
        super.postSetup();
    }

    @Override public BubbleNode start(BubbleNode node) throws Exception {

        final CloudRegion region = config.getRegion(node.getRegion());
        final ComputeNodeSize size = config.getSize(node.getSize());

        final Long regionId = getRegion(region.getInternalName()).getId();
        if (regionId == null) return die("start: region not found: "+region.getInternalName());

        final Long planId = getSize(size.getType()).getId();
        if (planId == null) return die("start: plan not found: "+size.getInternalName());

        final PackerImage packerImage = getOrCreatePackerImage(node);

        // prepare to create server
        final String data = "DCID=" + regionId +
                "&VPSPLANID=" + planId +
                "&OSID=" + getSnapshotOsId() +
                "&SNAPSHOTID=" + packerImage.getId() +
                "&tag=" + cloud.getUuid() +
                "&label=" + node.getFqdn() +
                "&enable_ipv6=yes";
        final HttpRequestBean serverRequest = auth(new HttpRequestBean(POST, CREATE_SERVER_URL, data));

        // create server, check response
        if (log.isInfoEnabled()) log.info("start: calling Vultr to start node: "+node.id());
        final HttpResponseBean serverResponse = serverRequest.curl();  // fixme: we can do better than shelling to curl
        if (serverResponse.getStatus() != 200) return die("start: error creating server: " + serverResponse);
        final JsonNode responseJson;
        try {
            responseJson = json(serverResponse.getEntityString(), JsonNode.class);
        } catch (IllegalStateException e) {
            return die("start: error creating server (error parsing response as JSON): " + serverResponse);
        }
        final var subId = responseJson.get(VULTR_SUBID).textValue();
        if (log.isDebugEnabled()) log.debug("start: Vultr started node: "+node.id()+" SUBID="+subId);

        node.setState(BubbleNodeState.booting);
        node.setTag(TAG_INSTANCE_ID, subId);
//        nodeDAO.update(node);

        final long start = now();
        boolean startedOk = false;
        final HttpRequestBean poll = auth(new HttpRequestBean(POLL_SERVER_URL+subId));
        sleep(SERVER_START_INITIAL_INTERVAL);
        while (now() - start < SERVER_START_TIMEOUT) {
            sleep(SERVER_START_POLL_INTERVAL);
            final HttpResponseBean pollResponse = getResponse(poll);
            if (pollResponse.getStatus() != OK) {
                return die("start: error polling node "+node.id()+" subid: "+subId+": "+pollResponse);
            }
            final JsonNode serverNode = json(pollResponse.getEntityString(), JsonNode.class);
            if (log.isDebugEnabled()) log.debug("start: polled node "+node.id()+" json="+json(serverNode, COMPACT_MAPPER));
            if (serverNode != null) {
                if (serverNode.has("tag")
                        && serverNode.get("tag").textValue().equals(cloud.getUuid())
                        && serverNode.has(VULTR_STATUS)
                        && serverNode.has(VULTR_SERVER_STATE)
                        && serverNode.has(VULTR_V4_IP)) {

                    final String status = serverNode.get(VULTR_STATUS).textValue();
                    final String serverState = serverNode.get(VULTR_SERVER_STATE).textValue();
                    final String ip4 = serverNode.get(VULTR_V4_IP).textValue();
                    final String ip6 = serverNode.get(VULTR_V6_IP).textValue();
                    // if (log.isInfoEnabled()) log.info("start: server_state="+serverState+", status="+status, "ip4="+ip4+", ip6="+ip6);

                    if (ip4 != null && ip4.length() > 0 && !ip4.equals("0.0.0.0")) {
                        node.setIp4(ip4);
//                        nodeDAO.update(node);
                    }
                    if (ip6 != null && ip6.length() > 0) {
                        node.setIp6(ip6);
//                        nodeDAO.update(node);
                    }
                    if (status.equals(VULTR_STATUS_ACTIVE) && (node.hasIp4() || node.hasIp6())) {
                        node.setState(BubbleNodeState.booted);
//                        nodeDAO.update(node);
                    }
                    if (serverState.equals(VULTR_STATE_OK)) {
                        if (log.isInfoEnabled()) log.info("start: server is ready: "+node.id());
                        startedOk = true;
                        break;
                    }
                }
            }
        }
        if (!startedOk) {
            if (log.isErrorEnabled()) log.error("start: timeout waiting for node "+node.id()+" to boot and become available, stopping it");
            stop(node);
        }
        return node;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception {
        return node;
    }

    private HttpRequestBean auth(HttpRequestBean req) {
        return req.setHeader(API_KEY_HEADER, credentials.getParam(PARAM_API_KEY));
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        Exception lastEx = null;
        final long start = now();
        while (now() - start < SERVER_STOP_TIMEOUT) {
            try {
                _stop(node);
            } catch (EntityNotFoundException e) {
                if (log.isInfoEnabled()) log.info("stop: node stopped");
                return node;

            } catch (Exception e) {
                if (log.isInfoEnabled()) log.info("stop: _stop failed with: "+shortError(e));
                lastEx = e;
            }
            sleep(SERVER_STOP_CHECK_INTERVAL, "stop: waiting to try stopping again until node is not found");
            if (log.isWarnEnabled()) log.warn("stop: node still running: "+node.id());
        }
        if (log.isErrorEnabled()) log.error("stop: error stopping node: "+node.id());
        if (lastEx != null) throw lastEx;
        return die("stop: timeout stopping node: "+node.id());
    }

    public BubbleNode _stop(BubbleNode node) throws IOException {
        // does the node still exist?
        BubbleNode vultrNode = listNode(node);
        if (vultrNode == null) {
            throw notFoundEx(node.id());
        }

        final String subId = vultrNode.getTag(TAG_INSTANCE_ID);
        if (subId == null) {
            if (log.isErrorEnabled()) log.error("_stop: node "+node.id()+" is missing tag "+TAG_INSTANCE_ID+", cannot stop, throwing invalidEx");
            throw invalidEx("err.node.stop.error", "stop: no " + VULTR_SUBID + " on node, returning");
        }

        if (log.isInfoEnabled()) log.info("_stop: calling stopServer("+subId+") for node "+node.id());
        stopServer(subId);
        return node;
    }

    private void stopServer(String subId) {
        final HttpRequestBean destroyServerRequest = auth(new HttpRequestBean(POST, DESTROY_SERVER_URL, VULTR_SUBID + "=" + subId));
        final HttpResponseBean destroyResponse = destroyServerRequest.curl();
        if (destroyResponse.getStatus() != OK) {
            throw invalidEx("err.node.stop.error", "stop: error stopping node: "+destroyResponse);
        }
    }

    private BubbleNode findByIp4(BubbleNode node, String ip4) throws IOException {
        final BubbleNode found = listNodes().stream()
                .filter(n -> n.hasIp4() && n.getIp4().equals(ip4))
                .findFirst()
                .orElse(null);
        if (found == null) {
            if (log.isWarnEnabled()) log.warn("stop: no subid tag found on node ("+node.getFqdn()+"/"+ ip4 +") and no server had this ip4");
            return null;
        }
        if (!found.hasTag(TAG_INSTANCE_ID)) {
            if (log.isWarnEnabled()) log.warn("stop: no subid tag found on node ("+node.getFqdn()+"/"+ ip4 +"), cannot stop");
            return null;
        }
        return found;
    }

    public BubbleNode listNode(BubbleNode node) {
        final HttpRequestBean listServerRequest = auth(new HttpRequestBean(POLL_SERVER_URL+node.getTag(TAG_INSTANCE_ID)));
        final HttpResponseBean listResponse = listServerRequest.curl();
        if (listResponse.getEntityString().startsWith("Invalid ")) return null;
        switch (listResponse.getStatus()) {
            case OK:
                try {
                    final JsonNode jsonNode = json(listResponse.getEntityString(), JsonNode.class);
                    final JsonNode subId = jsonNode.get(VULTR_SUBID);
                    final JsonNode ip4 = jsonNode.get(VULTR_V4_IP);
                    final JsonNode ip6 = jsonNode.get(VULTR_V6_IP);
                    if (log.isTraceEnabled()) log.trace("listNode("+node.id()+") found node: "+json(jsonNode, COMPACT_MAPPER));
                    if (subId != null) node.setTag(TAG_INSTANCE_ID, subId.textValue());
                    return node.setIp4(ip4 == null ? null : ip4.textValue()).setIp6(ip6 == null ? null : ip6.textValue());

                } catch (Exception e) {
                    if (log.isErrorEnabled()) log.error("listNode: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse+": exception="+shortError(e));
                    return null;
                }
            case NOT_FOUND:
                if (log.isDebugEnabled()) log.debug("listNode("+node.id()+") returned 404 Not Found");
                return null;
            default:
                if (log.isErrorEnabled()) log.error("listNode: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse);
                return null;
        }
    }

    @Override public List<BubbleNode> listNodes() throws IOException {
        return listNodes(server -> {
            final String tag = server.has("tag") ? server.get("tag").textValue() : null;
            return tag != null && tag.equals(cloud.getUuid());
        });
    }

    public List<BubbleNode> listNodes(Function<ObjectNode, Boolean> filter) throws IOException {
        final List<BubbleNode> nodes = new ArrayList<>();
        final HttpRequestBean listServerRequest = auth(new HttpRequestBean(LIST_SERVERS_URL));
        final HttpResponseBean listResponse = getResponse(listServerRequest);
        switch (listResponse.getStatus()) {
            case OK:
                final JsonNode entity = json(listResponse.getEntityString(), JsonNode.class);
                for (Iterator<String> iter = entity.fieldNames(); iter.hasNext(); ) {
                    final String subid = iter.next();
                    final ObjectNode server = (ObjectNode) entity.get(subid);
                    if (!filter.apply(server)) {
                        if (log.isTraceEnabled()) log.trace("Skipping node without cloud tag "+cloud.getUuid()+": "+subid);
                        continue;
                    }
                    final String subId = server.has(VULTR_SUBID) ? server.get(VULTR_SUBID).textValue() : null;
                    final String ip4 = server.has(VULTR_V4_IP) ? server.get(VULTR_V4_IP).textValue() : null;
                    final String ip6 = server.has(VULTR_V6_IP) ? server.get(VULTR_V6_IP).textValue() : null;
                    nodes.add(new BubbleNode().setIp4(ip4).setIp6(ip6).setTag(TAG_INSTANCE_ID, subId));
                }
                break;
            default:
                return die("listNode: error listing nodes, status="+listResponse.getStatus()+": "+listResponse);
        }
        return nodes;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        // find by label
        final HttpRequestBean listServerRequest = auth(new HttpRequestBean(LIST_SERVERS_URL+"?"+VULTR_LABEL+"="+node.getFqdn()));
        final HttpResponseBean listResponse = getResponse(listServerRequest);
        switch (listResponse.getStatus()) {
            case OK:
                final JsonNode entity = json(listResponse.getEntityString(), JsonNode.class);
                for (Iterator<String> iter = entity.fieldNames(); iter.hasNext(); ) {
                    final String subid = iter.next();
                    final ObjectNode server = (ObjectNode) entity.get(subid);
                    final String label = server.has(VULTR_LABEL) ? server.get(VULTR_LABEL).textValue() : "";
                    if (label.equals(node.getFqdn())) {
                        if (log.isDebugEnabled()) log.debug("status("+node.id()+"): found json: "+json(server, COMPACT_MAPPER));
                        if (server.has(VULTR_SERVER_STATE) && server.has(VULTR_STATUS)) {
                            final String status = server.get(VULTR_STATUS).textValue();
                            final String serverState = server.get(VULTR_SERVER_STATE).textValue();
                            final String ip4 = server.has(VULTR_V4_IP) ? server.get(VULTR_V4_IP).textValue() : null;
                            final String ip6 = server.has(VULTR_V6_IP) ? server.get(VULTR_V6_IP).textValue() : null;
                            node.setIp4(ip4).setIp6(ip6);
                            if (status.equals(VULTR_STATUS_PENDING) || serverState.equals(VULTR_STATE_NONE)) {
                                if (log.isDebugEnabled()) log.debug("status("+node.id()+"): pending/none: returning node status==starting");
                                return node.setState(BubbleNodeState.starting);
                            }
                            if (status.equals(VULTR_STATUS_ACTIVE)) {
                                if (serverState.equals(VULTR_STATE_OK)) {
                                    if (log.isDebugEnabled()) log.debug("status(" + node.id() + "): active/ok: returning node status==running");
                                    return node.setState(BubbleNodeState.running);

                                } else if (serverState.equals(VULTR_STATE_LOCKED)) {
                                    if (log.isDebugEnabled()) log.debug("status(" + node.id() + "): active/locked: returning node status==starting");
                                    return node.setState(BubbleNodeState.starting);
                                }
                            }
                            if (log.isDebugEnabled()) log.debug("status("+node.id()+"): status/state = "+status+"/"+serverState+": returning node status==unknown_error");
                            return node.setState(BubbleNodeState.unknown_error);
                        }
                    }
                }
                log.error("status: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse);
                return node.setState(BubbleNodeState.unknown_error);

            case NOT_FOUND: case PRECONDITION_FAILED:
                log.error("status: error response from API, returning unknown");
                return node.setState(BubbleNodeState.unknown_error);

            default:
                log.error("status: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse);
                return node.setState(BubbleNodeState.unknown_error);
        }
    }

    @Override public List<PackerImage> getAllPackerImages() { return getPackerImages(); }
    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return getPackerImages(); }

    public List<PackerImage> getPackerImages () {
        final List<PackerImage> images = loadCloudResources(SNAPSHOT_URL, new VultrPackerImageParser(configuration.getShortVersion(), packerService.getPackerPublicKeyHash()));
        return images == null ? Collections.emptyList() : images;
    }

    public static final long SNAPSHOT_TIMEOUT = MINUTES.toMillis(60);
    @Override public List<PackerImage> finalizeIncompletePackerRun(CommandResult commandResult, AnsibleInstallType installType) {
        if (!commandResult.getStdout().contains("Waiting 300s for snapshot")
                || !commandResult.getStdout().contains("Error waiting for snapshot")
                || !commandResult.getStdout().contains("Unable to destroy server: Unable to remove VM: Server is currently locked")) {
            stopImageServer(installType);
            return null;
        }

        // wait longer for the snapshot...
        final String keyHash = packerService.getPackerPublicKeyHash();
        final long start = now();
        PackerImage snapshot = null;
        while (now() - start < SNAPSHOT_TIMEOUT) {
            snapshot = getPackerImages().stream()
                    .filter(i -> i.getName().contains("_"+installType.name()+"_") && i.getName().contains(keyHash))
                    .findFirst()
                    .orElse(null);
            if (snapshot != null) break;
            sleep(SECONDS.toMillis(20), "finalizeIncompletePackerRun: waiting for snapshot: "+keyHash);
        }
        if (snapshot == null) {
            log.error("finalizeIncompletePackerRun: timeout waiting for snapshot");
        }

        if (!stopImageServer(installType)) return null;
        if (snapshot == null) return null;

        return new SingletonList<>(snapshot);
    }

    public boolean stopImageServer(AnsibleInstallType installType) {

        final String keyHash = packerService.getPackerPublicKeyHash();
        final List<BubbleNode> servers;

        // find the server(s)
        try {
            servers = listNodes(server -> {
                final String tag = server.has("tag") ? server.get("tag").textValue() : null;
                return tag != null && tag.contains("_"+installType.name()+"_") && tag.contains(keyHash);
            });
        } catch (IOException e) {
            log.error("stopImageServer: error listing servers: "+shortError(e), e);
            return false;
        }
        if (servers.isEmpty()) {
            log.error("stopImageServer: snapshot server not found");
            return false;
        }
        if (servers.size() != 1) {
            log.warn("stopImageServer: expected only one server, found: "+servers.size());
        }

        // now shut down the server(s)
        try {
            for (BubbleNode node : servers) {
                stopServer(node.getTag(TAG_INSTANCE_ID));
            }
        } catch (Exception e) {
            log.error("stopImageServer: error stopping server: "+shortError(e));
            return false;
        }
        return true;
    }

}
