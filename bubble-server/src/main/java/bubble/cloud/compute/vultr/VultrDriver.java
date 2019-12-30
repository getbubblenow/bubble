package bubble.cloud.compute.vultr;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeServiceDriverBase;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;

import java.io.IOException;
import java.util.*;

import static bubble.model.cloud.BubbleNode.TAG_INSTANCE_ID;
import static bubble.model.cloud.BubbleNode.TAG_SSH_KEY_ID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpHeaders.CONTENT_ENCODING;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.urlEncode;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class VultrDriver extends ComputeServiceDriverBase {

    public static final String API_KEY_HEADER = "API-Key";

    public static final String VULTR_API_BASE = "https://api.vultr.com/v1/";

    public static final String REGIONS_URL = VULTR_API_BASE + "regions/list";
    public static final String PLANS_URL = VULTR_API_BASE + "plans/list";
    public static final String OS_URL = VULTR_API_BASE + "os/list";

    public static final String CREATE_SSH_KEY_URL = VULTR_API_BASE + "sshkey/create";
    public static final String DESTROY_SSH_KEY_URL = VULTR_API_BASE + "sshkey/destroy";
    public static final String CREATE_SERVER_URL = VULTR_API_BASE + "server/create";
    public static final String DESTROY_SERVER_URL = VULTR_API_BASE + "server/destroy";
    public static final String LIST_SERVERS_URL = VULTR_API_BASE + "server/list";
    public static final String POLL_SERVER_URL = LIST_SERVERS_URL + "?SUBID=";

    @Getter(lazy=true) private static final Map<String, Integer> regionMap = getResourceMap(REGIONS_URL);
    @Getter(lazy=true) private static final Map<String, Integer> plansMap = getResourceMap(PLANS_URL);
    @Getter(lazy=true) private static final Map<String, Integer> osMap = getResourceMap(OS_URL);

    public static final long SERVER_START_POLL_INTERVAL = SECONDS.toMillis(5);
    public static final long SERVER_START_TIMEOUT = MINUTES.toMillis(10);

    private static Map<String, Integer> getResourceMap(String uri) {
        try {
            final HttpResponseBean response = getResponse(uri);
            final JsonNode regionsNode = json(response.getEntityString(), JsonNode.class);
            final Map<String, Integer> map = new HashMap<>();
            for (Iterator<String> fields = regionsNode.fieldNames(); fields.hasNext(); ) {
                final String regionId = fields.next();
                final JsonNode region = regionsNode.get(regionId);
                map.put(region.get("name").textValue(), Integer.parseInt(regionId));
            }
            return map;
        } catch (Exception e) {
            return die("getResourceMap("+uri+"): "+e, e);
        }
    }

    @Override public void postSetup() {
        if (credentials != null && credentials.hasParam(API_KEY_HEADER) && credentials.getParam(API_KEY_HEADER).contains("{{")) {
            final String apiKey = HandlebarsUtil.apply(getHandlebars(), credentials.getParam(API_KEY_HEADER), configuration.getEnvCtx());
            credentials.setParam(API_KEY_HEADER, apiKey);
        }
    }

    @Override public BubbleNode start(BubbleNode node) throws Exception {

        final CloudRegion region = config.getRegion(node.getRegion());
        final ComputeNodeSize size = config.getSize(node.getSize());

        final Integer regionId = getRegionMap().get(region.getInternalName());
        if (regionId == null) return die("start: region not found: "+region.getInternalName());

        final Integer planId = getPlansMap().get(size.getInternalName());
        if (planId == null) return die("start: plan not found: "+size.getInternalName());

        final Integer osId = getOsMap().get(config.getConfig("os"));
        if (osId == null) return die("start: OS not found: "+config.getConfig("os"));

        // register ssh key, check response
        final String sshKeyId = registerSshKey(node);

        // prepare to create server
        final String data = "DCID=" + regionId +
                "&VPSPLANID=" + planId +
                "&OSID=" + osId +
                "&SSHKEYID=" + sshKeyId +
                "&tag=" + cloud.getUuid() +
                "&label=" + node.getFqdn() +
                "&enable_ipv6=yes";
        final HttpRequestBean serverRequest = auth(new HttpRequestBean(POST, CREATE_SERVER_URL, data));

        // create server, check response
        final HttpResponseBean serverResponse = serverRequest.curl();  // fixme: we can do better than shelling to curl
        if (serverResponse.getStatus() != 200) return die("start: error creating server: " + serverResponse);
        final String subId = json(serverResponse.getEntityString(), JsonNode.class).get("SUBID").textValue();

        node.setState(BubbleNodeState.booting);
        node.setTag(TAG_INSTANCE_ID, subId);
        nodeDAO.update(node);

        final long start = now();
        boolean startedOk = false;
        final HttpRequestBean poll = auth(new HttpRequestBean(POLL_SERVER_URL+subId));
        while (now() - start < SERVER_START_TIMEOUT) {
            sleep(SERVER_START_POLL_INTERVAL);
            final HttpResponseBean pollResponse = getResponse(poll);
            if (pollResponse.getStatus() != OK) {
                return die("start: error polling subid: "+subId+": "+pollResponse);
            }
            // todo: add timeout, if server doesn't come up within X minutes, try to kill it and report an error
            final JsonNode serverNode = json(pollResponse.getEntityString(), JsonNode.class);
            if (serverNode != null) {
                if (serverNode.has("tag")
                        && serverNode.get("tag").textValue().equals(cloud.getUuid())
                        && serverNode.has("status")
                        && serverNode.has("main_ip")) {

                    final String status = serverNode.get("status").textValue();
                    final String ip4 = serverNode.get("main_ip").textValue();
                    if (ip4 != null && ip4.length() > 0 && !ip4.equals("0.0.0.0")) {
                        node.setIp4(ip4);
                        nodeDAO.update(node);
                    }
                    final String ip6 = serverNode.get("v6_main_ip").textValue();
                    if (ip6 != null && ip6.length() > 0) {
                        node.setIp6(ip6);
                        nodeDAO.update(node);
                    }
                    if (status.equals("active") && (node.hasIp4() || node.hasIp6())) {
                        log.info("start: node is active! we can run ansible now: " + node.getIp4());
                        node.setState(BubbleNodeState.booted);
                        nodeDAO.update(node);
                        startedOk = true;
                        break;
                    }
                }
            }
        }
        if (!startedOk) {
            log.error("start: timeout waiting for node to boot and become available, stopping it");
            stop(node);
        }
        return node;
    }

    @Override public HttpRequestBean registerSshKeyRequest(BubbleNode node) {
        final String keyData = "name="+urlEncode(node.getUuid())+"&ssh_key="+urlEncode(node.getSshKey().getSshPublicKey());
        return auth(new HttpRequestBean(POST, CREATE_SSH_KEY_URL, keyData).setHeader(CONTENT_ENCODING, "application/x-www-form-urlencoded"));
    }

    @Override protected String readSshKeyId(HttpResponseBean keyResponse)  {
        return json(keyResponse.getEntityString(), JsonNode.class).get("SSHKEYID").textValue();
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception {
        deleteVultrKey(node);
        return node;
    }

    private HttpRequestBean auth(HttpRequestBean req) {
        return req.setHeader(API_KEY_HEADER, credentials.getParam(API_KEY_HEADER));
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {

        deleteVultrKey(node); // just in case

        BubbleNode vultrNode;
        final String ip4 = node.getIp4();
        if (!node.hasTag(TAG_INSTANCE_ID)) {
            if (ip4 == null) {
                throw notFoundEx(node.id());
            }
            log.warn("stop: no subid tag found on node ("+node.getFqdn()+"/"+ ip4 +"), searching based in ip4...");
            vultrNode = findByIp4(node, ip4);
        } else {
            // does the node still exist?
            vultrNode = listNode(node);
            if (vultrNode == null) {
                vultrNode = findByIp4(node, ip4);
            }
        }
        if (vultrNode == null) {
            throw notFoundEx(node.id());
        }

        final String subId = vultrNode.getTag(TAG_INSTANCE_ID);
        if (subId == null) {
            throw invalidEx("err.node.stop.error", "stop: no SUBID on node, returning");
        }

        final HttpRequestBean destroyServerRequest = auth(new HttpRequestBean(POST, DESTROY_SERVER_URL, "SUBID="+ subId));
        final HttpResponseBean destroyResponse = destroyServerRequest.curl();
        if (destroyResponse.getStatus() != OK) {
            throw invalidEx("err.node.stop.error", "stop: error stopping node: "+destroyResponse);
        }
        return node;
    }

    private BubbleNode findByIp4(BubbleNode node, String ip4) throws IOException {
        final BubbleNode found = listNodes().stream()
                .filter(n -> n.hasIp4() && n.getIp4().equals(ip4))
                .findFirst()
                .orElse(null);
        if (found == null) {
            log.warn("stop: no subid tag found on node ("+node.getFqdn()+"/"+ ip4 +") and no server had this ip4");
            return null;
        }
        if (!found.hasTag(TAG_INSTANCE_ID)) {
            log.warn("stop: no subid tag found on node ("+node.getFqdn()+"/"+ ip4 +"), cannot stop");
            return null;
        }
        return found;
    }

    public BubbleNode listNode(BubbleNode node) throws IOException {
        final HttpRequestBean listServerRequest = auth(new HttpRequestBean(POLL_SERVER_URL+node.getTag(TAG_INSTANCE_ID)));
        final HttpResponseBean listResponse = getResponse(listServerRequest);
        switch (listResponse.getStatus()) {
            case OK:
                break;
            case NOT_FOUND: case PRECONDITION_FAILED:
                log.warn("stop: node "+node.id()+" is already stopped? http status: "+listResponse.getStatus());
                return node;
            default:
                return die("listNode: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse);
        }
        return null;
    }

    @Override public List<BubbleNode> listNodes() throws IOException {
        final List<BubbleNode> nodes = new ArrayList<>();
        final HttpRequestBean listServerRequest = auth(new HttpRequestBean(LIST_SERVERS_URL));
        final HttpResponseBean listResponse = getResponse(listServerRequest);
        switch (listResponse.getStatus()) {
            case OK:
                final JsonNode entity = json(listResponse.getEntityString(), JsonNode.class);
                for (Iterator<String> iter = entity.fieldNames(); iter.hasNext(); ) {
                    final String subid = iter.next();
                    final ObjectNode server = (ObjectNode) entity.get(subid);
                    final String tag = server.has("tag") ? server.get("tag").textValue() : null;
                    if (tag == null || !tag.equals(cloud.getUuid())) {
                        log.debug("Skipping node without cloud tag "+cloud.getUuid()+": "+subid);
                        continue;
                    }
                    final String subId = server.has("SUBID") ? server.get("SUBID").textValue() : null;
                    final String ip4 = server.has("main_ip") ? server.get("main_ip").textValue() : null;
                    final String ip6 = server.has("v6_main_ip") ? server.get("v6_main_ip").textValue() : null;
                    nodes.add(new BubbleNode().setIp4(ip4).setIp6(ip6).setTag(TAG_INSTANCE_ID, subId));
                }
                break;
            default:
                return die("listNode: error listing nodes, status="+listResponse.getStatus()+": "+listResponse);
        }
        return nodes;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        if (node.hasTag(TAG_INSTANCE_ID)) {
            final BubbleNode found = listNode(node);
            if (found == null) return node.setState(BubbleNodeState.stopped);
            return node;

        } else if (node.hasIp4()) {
            // find by IPv4
            final HttpRequestBean listServerRequest = auth(new HttpRequestBean(LIST_SERVERS_URL));
            final HttpResponseBean listResponse = getResponse(listServerRequest);
            switch (listResponse.getStatus()) {
                case OK:
                    final JsonNode entity = json(listResponse.getEntityString(), JsonNode.class);
                    for (Iterator<String> iter = entity.fieldNames(); iter.hasNext(); ) {
                        final String subid = iter.next();
                        final ObjectNode server = (ObjectNode) entity.get(subid);
                        final String ip4 = server.has("main_ip") ? server.get("main_ip").textValue() : "";
                        if (ip4.equals(node.getIp4())) {
                            if (server.has("power_status") && server.get("power_status").textValue().equals("running")
                                    && server.has("server_state") && server.get("server_state").textValue().equals("ok")) {
                                final String ip6 = server.has("v6_main_ip") ? server.get("v6_main_ip").textValue() : null;
                                return node.setIp4(ip4).setIp6(ip6).setState(BubbleNodeState.running);
                            }
                        }
                    }
                case NOT_FOUND: case PRECONDITION_FAILED:
                    log.error("status: error response from API, returning unknown");
                    return node.setState(BubbleNodeState.unknown_error);

                default:
                    log.error("status: error finding node "+node.id()+", status="+listResponse.getStatus()+": "+listResponse);
                    return node.setState(BubbleNodeState.unknown_error);
            }
        } else {
            // Node has no IP4
            return node.setState(BubbleNodeState.unknown_error);
        }
    }

    public void deleteVultrKey(BubbleNode node) throws IOException {
        if (node.hasTag(TAG_SSH_KEY_ID)) {
            final String keyId = node.getTag(TAG_SSH_KEY_ID);
            final String keyData = "SSHKEYID="+ keyId;
            final HttpRequestBean destroyKeyRequest = auth(new HttpRequestBean(POST, DESTROY_SSH_KEY_URL, keyData));

            // destroy key, check response
            final HttpResponseBean destroyKeyResponse = destroyKeyRequest.curl();
            if (destroyKeyResponse.getStatus() != OK) {
                log.warn("cleanupStart: error destroying sshkey: "+ keyId);
            }
        }
    }
}
