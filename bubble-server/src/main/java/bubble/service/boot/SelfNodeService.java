package bubble.service.boot;

import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;

public interface SelfNodeService {

    boolean initThisNode(BubbleNode thisNode);

    BubbleNode getThisNode ();

    BubbleNetwork getThisNetwork();

    void refreshThisNetwork ();

}
