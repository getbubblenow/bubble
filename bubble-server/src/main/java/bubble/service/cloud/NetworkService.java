/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;

public interface NetworkService {

    boolean stopNetwork(BubbleNetwork network);

    boolean isReachable(BubbleNode node);

    BubbleNode killNode(BubbleNode node, String forceDelete);

}
