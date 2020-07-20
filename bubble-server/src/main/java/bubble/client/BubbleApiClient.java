/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.client;

import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.wizard.client.ApiClientBase;

import static bubble.ApiConstants.SESSION_HEADER;

public class BubbleApiClient extends ApiClientBase {

    @SuppressWarnings("unused") // called by ApiClientBase.copy
    public BubbleApiClient(BubbleApiClient other) {
        this(other.getConnectionInfo());
        setToken(other.getToken());
    }

    public BubbleApiClient(ApiConnectionInfo connectionInfo) { super(connectionInfo); }

    public BubbleApiClient(BubbleNode node, BubbleConfiguration configuration) {
        super(new ApiConnectionInfo(configuration.nodeBaseUri(node)));
    }

    @Override public String getTokenHeader() { return SESSION_HEADER; }

    public static HttpClientBuilder newHttpClientBuilder(int max, int maxPerRoute) {
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(max);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        return HttpClientBuilder.create().setConnectionManager(cm);
    }

    @Override public HttpClientBuilder getHttpClientBuilder() {
        return newHttpClientBuilder(5, 5);
    }

}
