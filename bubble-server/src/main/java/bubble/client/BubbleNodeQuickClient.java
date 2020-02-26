/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.client;

import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;

import static java.util.concurrent.TimeUnit.SECONDS;

public class BubbleNodeQuickClient extends BubbleNodeClient {

    public static final int NUM_TRIES = 2;
    public static final int QUICK_CONNECT_TIMEOUT = (int) SECONDS.toMillis(5);
    public static final int QUICK_SOCKET_TIMEOUT = (int) SECONDS.toMillis(5);
    public static final int QUICK_REQUEST_TIMEOUT = (int) SECONDS.toMillis(5);

    public BubbleNodeQuickClient(BubbleNode node, BubbleConfiguration configuration) {
        super(node, configuration);
        init();
    }

    public void init() {
        setNumTries(NUM_TRIES);
        setConnectTimeout(QUICK_CONNECT_TIMEOUT);
        setSocketTimeout(QUICK_SOCKET_TIMEOUT);
        setRequestTimeout(QUICK_REQUEST_TIMEOUT);
    }

}
