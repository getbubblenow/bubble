/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.service.boot.SelfNodeService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterSelfNodeService implements SelfNodeService {

    @Override public boolean initThisNode(BubbleNode thisNode) { return notSupported("initThisNode"); }

    @Override public BubbleNode getThisNode() { return notSupported("getThisNode"); }

    @Override public BubbleNetwork getThisNetwork() { return notSupported("getThisNetwork"); }

    @Override public void refreshThisNetwork() { notSupported("refreshThisNetwork"); }

    @Override public BubbleNode getSoleNode() { return notSupported("getSoleNode"); }

    @Override public void setActivated(BubbleNode thisNode) { notSupported("setActivated"); }

    @Override public BubblePlan getThisPlan() { return notSupported("getThisPlan"); }

    @Override public Boolean getLogFlag() { return notSupported("getLogFlag"); }
    @Override public void setLogFlag(boolean logFlag) { notSupported("setLogFlag"); }

}
