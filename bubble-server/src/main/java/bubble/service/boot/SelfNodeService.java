/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import lombok.NonNull;

import java.util.Optional;

public interface SelfNodeService {

    boolean initThisNode(BubbleNode thisNode);

    BubbleNode getSageNode();

    BubbleNode getThisNode ();

    BubbleNetwork getThisNetwork();

    void refreshThisNetwork ();

    BubbleNode getSoleNode();

    void setActivated(BubbleNode thisNode);

    BubblePlan getThisPlan();

    boolean getLogFlag();

    /**
     * @return Empty if TTL for log flag is a special one (less than 0), else timestamp (milliseconds) when log flag
     *         will be expired (to be precise, a very close time after the real expiration time as some time is spent
     *         for processing here).
     */
    @NonNull Optional<Long> getLogFlagExpirationTime();
    void setLogFlag(final boolean logFlag, @NonNull final Optional<Integer> ttlInSeconds);

}
