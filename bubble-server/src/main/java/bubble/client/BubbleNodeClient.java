package bubble.client;

import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.wizard.server.config.HttpConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.isHttpsPort;
import static bubble.server.BubbleServer.getRestoreKey;
import static bubble.server.BubbleServer.isRestoreMode;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class BubbleNodeClient extends BubbleApiClient {

    public static final String H_BUBBLE_FROM_NODE_UUID = "X-Bubble-From-Node-UUID";
    public static final String H_BUBBLE_FROM_NODE_KEY = "X-Bubble-From-Node-Key";
    public static final String H_BUBBLE_TO_NODE_KEY = "X-Bubble-To-Node-Key";
    public static final String H_BUBBLE_RESTORE_KEY = "X-Bubble-Restore-Key";

    private BubbleNode fromNode;
    private BubbleNodeKey fromKey;
    private BubbleNode toNode;
    private BubbleNodeKey toKey;
    private BubbleApiClient alternate;
    private boolean useAlternate = false;

    public BubbleNodeClient(BubbleNode toNode, BubbleConfiguration configuration) {
        // use http if connection is to localhost
        super(new ApiConnectionInfo(baseUri(toNode, configuration)));
        initKeys(toNode, configuration);
        alternate = getAlternate(toNode, configuration);
    }

    // ensure we have at least one valid key so others can talk to us
    public BubbleNodeClient(BubbleNode toNode, BubbleConfiguration configuration, boolean alternate) {
        super(new ApiConnectionInfo(baseUri(toNode, configuration)));
        initKeys(toNode, configuration);
        this.alternate = null;
    }

    public void initKeys(BubbleNode toNode, BubbleConfiguration configuration) {
        final BubbleNodeKeyDAO nodeKeyDAO = configuration.getBean(BubbleNodeKeyDAO.class);

        toKey = nodeKeyDAO.findFirstByNode(toNode.getUuid());
        if (toKey == null) die("initKeys: no toKey found for toNode: "+ toNode.getUuid());

        fromNode = configuration.getThisNode();

        // ensure we have at least one valid key so others can talk to us
        final List<BubbleNodeKey> keys = nodeKeyDAO.findByNode(fromNode.getUuid());
        if (BubbleNodeKey.shouldGenerateNewKey(keys)) {
            fromKey = nodeKeyDAO.create(new BubbleNodeKey(fromNode));
        } else {
            fromKey = keys.get(0);
        }

        this.toNode = toNode;
    }

    public BubbleNodeClient getAlternate(BubbleNode node, BubbleConfiguration configuration) {
        return new BubbleNodeClient(node, configuration, true);
    }

    private static String baseUri(BubbleNode node, BubbleConfiguration configuration) {
        final HttpConfiguration http = configuration.getHttp();

        if (node.getUuid().equals(configuration.getThisNode().getUuid())) {
            return "http://127.0.0.1:"+ http.getPort()+ http.getBaseUri();
        }
        return (isHttpsPort(node.getSslPort()) ? "https://" : "http://")
                + node.getFqdn() + ":" + node.getSslPort() + http.getBaseUri();
    }

    @Override protected <T> void setRequestEntity(HttpEntityEnclosingRequest entityRequest, T data, ContentType contentType) {
        if (data instanceof InputStream) notSupported("setRequestEntity: InputStream not supported");
        super.setRequestEntity(entityRequest, json(fromKey.encrypt(data.toString(), toKey.getRsaKey())), contentType);
    }

    @Override protected String getJson(HttpRequestBean requestBean) throws Exception {
        return notSupported("setRequestEntity: getJson not supported");
    }

    @Override public Map<String, String> getHeaders() {
        Map<String, String> headers = super.getHeaders();
        if (headers == null) headers = new HashMap<>();
        headers.put(H_BUBBLE_FROM_NODE_UUID, fromNode.getUuid());
        headers.put(H_BUBBLE_FROM_NODE_KEY, fromKey.getUuid());
        headers.put(H_BUBBLE_TO_NODE_KEY, toKey.getUuid());
        if (isRestoreMode()) {
            log.debug("getHeaders: adding restore key: "+getRestoreKey());
            headers.put(H_BUBBLE_RESTORE_KEY, getRestoreKey());
        }
        log.debug("getHeaders: returning "+json(headers, COMPACT_MAPPER));
        return headers;
    }

    @Override public HttpResponse execute(HttpClient client, HttpRequestBase request) throws IOException {
        if (useAlternate) {
            log.info("execute: useAlternate true, using alternate...");
            return alternate.execute(client, request);
        }
        try {
            log.debug("execute: attempting request...");
            return super.execute(client, request);
        } catch (Exception e) {
            log.info("execute("+request+"): error: "+e);
            if (alternate == null) throw e;

            final String uri = (isHttpsPort(toNode.getSslPort()) ? "https://" : "http://")
                    + toNode.getIp4() + ":" + toNode.getAdminPort() + URIUtil.getPath(request.getURI().toString());
            request.setURI(URI.create(uri));
            log.info("execute: api call failed, trying alternate...");
            final HttpResponse response = alternate.execute(client, request);
            useAlternate = true;
            log.info("execute: api call failed, alternate succeeded, will continue using that");
            return response;
        }
    }

    @Override public void close() {
        super.close();
        if (alternate != null) alternate.close();
    }

}
