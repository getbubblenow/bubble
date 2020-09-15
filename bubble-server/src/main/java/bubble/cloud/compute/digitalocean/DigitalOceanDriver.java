/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.*;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static bubble.model.cloud.BubbleNode.TAG_INSTANCE_ID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.DELETE;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.http.HttpStatusCodes.NO_CONTENT;
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_UNKNOWN_FIELDS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class DigitalOceanDriver extends ComputeServiceDriverBase {

    public static final String PARAM_API_KEY = "apiKey";

    public static final String DO_API_BASE = "https://api.digitalocean.com/v2/";

    public static final String TAG_PREFIX_CLOUD = "cloud_";
    public static final String TAG_PREFIX_NODE = "node_";
    public static final String PACKER_IMAGES_URI = "images?private=true";

    @Getter(lazy=true) private final List<CloudRegion> cloudRegions = getResources("regions", new DigitalOceanRegionParser());
    @Getter(lazy=true) private final List<ComputeNodeSize> cloudSizes = getResources("sizes", new DigitalOceanComputeNodeSizeParser());
    @Getter(lazy=true) private final List<OsImage> cloudOsImages = getResources("images?type=distribution", new DigitalOceanOsImageParser());

    private <E, C extends Collection<E>> C getResources(String uri, ResourceParser<E, C> parser) {
        final int qPos = uri.indexOf('?');
        final String type = qPos == -1 ? uri : uri.substring(0, qPos);
        final JsonNode found = doGet(uri, JsonNode.class);
        final C results = parser.newResults();

        JsonNode page = found;
        do {
            final JsonNode items = page.has(type) ? page.get(type) : null;
            if (empty(items)) {
                if (!parser.allowEmpty()) return die("getResources("+uri+"): expected "+type+" property to contain a (non-empty) array");
                return null;
            }
            if (!items.isArray()) return die("getResources("+uri+"): expected "+type+" property to contain a (non-empty) array");

            for (int i=0; i<items.size(); i++) {
                final E item = parser.parse(items.get(i));
                if (item != null) results.add(item);
            }

            final String next = getNext(page);
            page = next == null ? null : doGet(next, JsonNode.class, false);

        } while (page != null);

        return results;
    }

    private String getNext(JsonNode node) {
        try {
            return node.has("links")
                    && node.get("links").has("pages")
                    && node.get("links").get("pages").has("next")
                    ? node.get("links").get("pages").get("next").textValue()
                    : null;
        } catch (Exception e) {
            log.warn("getNext: "+e);
            return null;
        }
    }

    private HttpRequestBean auth(HttpRequestBean request) {
        return request.setHeader(AUTHORIZATION, "Bearer "+getCredentials().getParam(PARAM_API_KEY));
    }

    private HttpRequestBean postRequest(String uri, String json) {
        return auth(new HttpRequestBean()
                .setMethod(POST)
                .setUri(DO_API_BASE+uri)
                .setEntity(json)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON));
    }

    private <T> T doPost(String uri, String json, Class<T> clazz) {
        final HttpResponseBean response;
        try {
            response = getResponse(postRequest(uri, json));
        } catch (IOException e) {
            return die("doGet("+uri+"): "+e);
        }
        if (!response.isOk()) return die("doPost("+uri+"): HTTP "+response.getStatus());
        return json(response.getEntityString(), clazz, FULL_MAPPER_ALLOW_UNKNOWN_FIELDS);
    }

    private <T> T doGet(String uri, Class<T> clazz) { return doGet(uri, clazz, true); }

    private <T> T doGet(String uri, Class<T> clazz, boolean addPrefix) {
        final HttpResponseBean response;
        try {
            response = getResponse(auth(new HttpRequestBean(addPrefix ? DO_API_BASE + uri : uri)));
        } catch (IOException e) {
            return die("doGet("+uri+"): "+e);
        }
        if (!response.isOk()) return die("doGet("+uri+"): HTTP "+response.getStatus());
        return json(response.getEntityString(), clazz, FULL_MAPPER_ALLOW_UNKNOWN_FIELDS);
    }

    @Override public List<BubbleNode> listNodes() throws IOException { return listNodes(TAG_PREFIX_CLOUD+cloud.getUuid()); }

    public List<BubbleNode> listNodes(String tag) throws IOException {
        final ListDropletsResponse response = doGet("droplets?tag_name=" + tag, ListDropletsResponse.class);
        final List<BubbleNode> nodes = new ArrayList<>();
        if (response.hasDroplets()) {
            for (Droplet droplet : response.getDroplets()) {
                final BubbleNode n = new BubbleNode()
                        .setFqdn(droplet.getName())
                        .setIp4(droplet.getIp4())
                        .setIp6(droplet.getIp6())
                        .setState(getNodeState(droplet.getStatus()))
                        .setTag(TAG_INSTANCE_ID, droplet.getId());
                n.setUuid(droplet.getTagWithPrefix(TAG_PREFIX_NODE));
                nodes.add(n);
            }
        }
        return nodes;
    }

    private BubbleNodeState getNodeState(String status) {
        switch (status) {
            case "active":              return BubbleNodeState.running;
            case "new":                 return BubbleNodeState.booting;
            case "off": case "archive": return BubbleNodeState.stopped;
            default: return die("getNodeState("+status+") no mapping for "+status);
        }
    }

    public static final long SERVER_START_POLL_INTERVAL = SECONDS.toMillis(5);
    public static final long SERVER_START_TIMEOUT = MINUTES.toMillis(10);

    @Override public BubbleNode start(BubbleNode node) throws Exception {

        final CloudRegion region = getRegions().stream()
                .filter(r -> r.getInternalName().equals(node.getRegion()))
                .findFirst()
                .orElse(null);
        if (region == null) return die("start: region not found: " + node.getRegion());

        final ComputeNodeSize size = config.getSize(node.getSize());

        final PackerImage packerImage = getOrCreatePackerImage(node);

        final CreateDropletRequest createRequest = new CreateDropletRequest()
                .setName(node.getFqdn())
                .setRegion(region.getInternalName())
                .setSize(size.getInternalName())
                .setImage(packerImage.getId())
                .setIpv6(true)
                .setBackups(false)
                .setMonitoring(false)
                .setPrivate_networking(false)
                .setTags(new String[] {TAG_PREFIX_CLOUD+cloud.getUuid(), TAG_PREFIX_NODE+node.getUuid()});

        final CreateDropletResponse droplet = doPost("droplets", json(createRequest), CreateDropletResponse.class);
        node.setState(BubbleNodeState.booting);
        node.setTag(TAG_INSTANCE_ID, droplet.getDroplet().getId());
        nodeDAO.update(node);

        final long start = now();
        boolean startedOk = false;
        while (now() - start < SERVER_START_TIMEOUT) {
            sleep(SERVER_START_POLL_INTERVAL);
            final List<BubbleNode> found = listNodes(TAG_PREFIX_NODE+node.getUuid());
            if (found.isEmpty()) continue;
            final BubbleNode newNode = found.get(0);
            if (newNode.hasIp4() && newNode.hasIp6() && newNode.getState() == BubbleNodeState.running) {
                node.setIp4(newNode.getIp4());
                node.setIp6(newNode.getIp6());
                node.setState(BubbleNodeState.booted);
                nodeDAO.update(node);
                startedOk = true;
                break;
            }
        }
        if (!startedOk) {
            log.error("start: timeout waiting for node to boot and become available, stopping it");
            stop(node);
        }
        return node;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) { return node; }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        cleanupStart(node); // just in case the key is still around
        final HttpRequestBean destroyDropletRequest = auth(new HttpRequestBean()
                .setMethod(DELETE)
                .setUri(DO_API_BASE+"droplets?tag_name="+TAG_PREFIX_NODE+node.getUuid()));
        final HttpResponseBean response = getResponse(destroyDropletRequest);
        if (response.getStatus() != NO_CONTENT) {
            throw invalidEx("err.node.stop.error", "stop: error stopping node: "+response);
        }
        return node;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final List<BubbleNode> found = listNodes(TAG_PREFIX_NODE+node.getUuid());
        if (found.isEmpty()) return node.setState(BubbleNodeState.stopped);
        return node.setState(found.get(0).getState());
    }

    @Override public List<PackerImage> getAllPackerImages() { return getPackerImages(); }
    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return getPackerImages(); }

    public List<PackerImage> getPackerImages () {
        final List<PackerImage> images = getResources(PACKER_IMAGES_URI, new DigitalOceanPackerImageParser(configuration.getShortVersion(), packerService.getPackerVersionHash()));
        return images == null ? Collections.emptyList() : images;
    }

}
