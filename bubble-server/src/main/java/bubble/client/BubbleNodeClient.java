/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
import org.cobbzilla.wizard.server.config.HttpConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.server.BubbleServer.getRestoreKey;
import static bubble.server.BubbleServer.isRestoreMode;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTPS;
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

    public BubbleNodeClient(BubbleNode toNode, BubbleConfiguration configuration) {
        // use http if connection is to localhost
        super(new ApiConnectionInfo(baseUri(toNode, configuration)));
        initKeys(toNode, configuration);
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

    private static String baseUri(BubbleNode node, BubbleConfiguration configuration) {
        final HttpConfiguration http = configuration.getHttp();
        return SCHEME_HTTPS + node.getFqdn() + ":" + node.getSslPort() + http.getBaseUri();
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
        try {
            log.debug("execute: attempting request...");
            return super.execute(client, request);
        } catch (Exception e) {
            return die("execute("+request+"): error: "+e);
        }
    }

}
