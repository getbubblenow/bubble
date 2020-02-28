/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.client;

import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BubbleNodeDownloadClient extends BubbleNodeClient {

    public static final int NUM_TRIES = 10;
    public static final int DL_CONNECT_TIMEOUT = (int) SECONDS.toMillis(30);
    public static final int DL_SOCKET_TIMEOUT = (int) MINUTES.toMillis(20);
    public static final int DL_REQUEST_TIMEOUT = (int) MINUTES.toMillis(20);

    public BubbleNodeDownloadClient(BubbleNode node, BubbleConfiguration configuration) {
        super(node, configuration);
        init();
    }

    public void init() {
        setNumTries(NUM_TRIES);
        setConnectTimeout(DL_CONNECT_TIMEOUT);
        setSocketTimeout(DL_SOCKET_TIMEOUT);
        setRequestTimeout(DL_REQUEST_TIMEOUT);
    }

}

