package bubble.cloud.compute.digitalocean;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeServiceDriverBase;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import java.io.IOException;
import java.util.*;

import static bubble.model.cloud.BubbleNode.TAG_INSTANCE_ID;
import static bubble.model.cloud.BubbleNode.TAG_SSH_KEY_ID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.DELETE;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.http.HttpStatusCodes.NO_CONTENT;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class DigitalOceanDriver extends ComputeServiceDriverBase {

    private static final String PARAM_API_KEY = "apiKey";

    public static final String DO_API_BASE = "https://api.digitalocean.com/v2/";

    public static final String TAG_PREFIX_CLOUD = "cloud_";
    public static final String TAG_PREFIX_NODE = "node_";

    @Getter(lazy=true) private final Set<String> regionSlugs = getResourceSlugs("regions");
    @Getter(lazy=true) private final Set<String> sizeSlugs = getResourceSlugs("sizes");
    @Getter(lazy=true) private final Set<String> imageSlugs = getResourceSlugs("images");

    private Set<String> getResourceSlugs(String type) {
        final JsonNode found = doGet(type, JsonNode.class);
        final JsonNode items = found.get(type);
        if (!items.isArray()) return die("getResourceSlugs("+type+"): expected "+type+" property to contain an array");
        final Set<String> slugs = new HashSet<>();
        for (int i=0; i<items.size(); i++) {
            final JsonNode slug = items.get(i).get("slug");
            if (slug == null) return die("getResourceSlugs("+type+"): no 'slug' found in item: "+json(items.get(i)));
            slugs.add(slug.textValue());
        }
        return slugs;
    }

    private HttpRequestBean auth(HttpRequestBean request) {
        return request.setHeader(AUTHORIZATION, "Bearer "+getCredentials().getParam(PARAM_API_KEY));
    }

    private HttpRequestBean postRequest(String uri, String json) {
        return auth(new HttpRequestBean(POST)
                .setUri(DO_API_BASE+uri)
                .setEntity(json)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON));
    }

    private <T> T doPost(String uri, String json, Class<T> clazz) {
        final HttpResponseBean response;
        try {
            response = HttpUtil.getResponse(postRequest(uri, json));
        } catch (IOException e) {
            return die("doGet("+uri+"): "+e);
        }
        if (!response.isOk()) return die("doPost("+uri+"): HTTP "+response.getStatus());
        return json(response.getEntityString(), clazz);
    }

    private <T> T doGet(String uri, Class<T> clazz) {
        final HttpResponseBean response;
        try {
            response = HttpUtil.getResponse(auth(new HttpRequestBean(DO_API_BASE + uri)));
        } catch (IOException e) {
            return die("doGet("+uri+"): "+e);
        }
        if (!response.isOk()) return die("doGet("+uri+"): HTTP "+response.getStatus());
        return json(response.getEntityString(), clazz);
    }

    @Override protected HttpRequestBean registerSshKeyRequest(BubbleNode node) {
        final Map<String, String> key = new LinkedHashMap<>();
        key.put("name", node.getUuid()+"_"+now());
        key.put("public_key", node.getSshKey().getSshPublicKey());
        return postRequest("account/keys", json(key));
    }

    @Override protected String readSshKeyId(HttpResponseBean keyResponse) {
        return json(keyResponse.getEntityString(), JsonNode.class).get("id").textValue();
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

        final CloudRegion region = config.getRegion(node.getRegion());
        final ComputeNodeSize size = config.getSize(node.getSize());
        final String os = config.getConfig("os");

        if (!getRegionSlugs().contains(region.getInternalName())) {
            return die("start: region not found: " + region.getInternalName());
        }
        if (!getSizeSlugs().contains(size.getInternalName())) {
            return die("start: region not found: " + region.getInternalName());
        }
        if (!getImageSlugs().contains(os)) {
            return die("start: region not found: " + region.getInternalName());
        }

        final String sshKeyId = registerSshKey(node);

        final CreateDropletRequest createRequest = new CreateDropletRequest()
                .setName(node.getFqdn())
                .setRegion(region.getInternalName())
                .setSize(size.getInternalName())
                .setImage(os)
                .setIpv6(true)
                .setBackups(false)
                .setMonitoring(false)
                .setPrivate_networking(false)
                .setSsh_keys(new Integer[] {Integer.valueOf(sshKeyId)})
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
            }
        }
        if (!startedOk) {
            log.error("start: timeout waiting for node to boot and become available, stopping it");
            stop(node);
        }
        return node;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception {
        if (node.hasTag(TAG_SSH_KEY_ID)) {
            final String keyId = node.getTag(TAG_SSH_KEY_ID);
            final HttpRequestBean destroyKeyRequest = auth(new HttpRequestBean(DELETE, "account/keys/"+keyId));

            // destroy key, check response
            final HttpResponseBean destroyKeyResponse = HttpUtil.getResponse(destroyKeyRequest);
            if (destroyKeyResponse.getStatus() != NO_CONTENT) {
                log.warn("cleanupStart: error destroying sshkey: "+ keyId);
            }
        }
        return node;
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        cleanupStart(node); // just in case the key is still around
        final HttpRequestBean destroyDropletRequest = auth(new HttpRequestBean(DELETE, "droplets?tag_name="+node.getUuid()));
        final HttpResponseBean response = HttpUtil.getResponse(destroyDropletRequest);
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

}
