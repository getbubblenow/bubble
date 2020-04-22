/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.service.cloud.NetworkService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterNetworkService implements NetworkService {

    @Override public boolean stopNetwork(BubbleNetwork network) { return notSupported("stopNetwork"); }

    @Override public boolean isReachable(BubbleNode node) { return notSupported("isReachable"); }

    @Override public BubbleNode killNode(BubbleNode node, String forceDelete) { return notSupported("killNode"); }

}
