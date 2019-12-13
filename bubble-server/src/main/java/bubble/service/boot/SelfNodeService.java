package bubble.service.boot;

import bubble.model.cloud.BubbleNetwork;

public interface SelfNodeService {

    BubbleNetwork getThisNetwork();

    void refreshThisNetwork ();

}
