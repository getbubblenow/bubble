/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.boot;

import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;

public interface SelfNodeService {

    boolean initThisNode(BubbleNode thisNode);

    BubbleNode getThisNode ();

    BubbleNetwork getThisNetwork();

    void refreshThisNetwork ();

    BubbleNode getSoleNode();

    void setActivated(BubbleNode thisNode);

    BubblePlan getThisPlan();
}
