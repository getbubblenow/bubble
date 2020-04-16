/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.RegionalServiceDriver;

import java.util.List;

public interface ComputeServiceDriver extends CloudServiceDriver, RegionalServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.compute; }

    List<ComputeNodeSize> getSizes();
    ComputeNodeSize getSize(ComputeNodeSizeType type);

    BubbleNode start(BubbleNode node) throws Exception;
    BubbleNode cleanupStart(BubbleNode node) throws Exception;
    BubbleNode stop(BubbleNode node) throws Exception;
    BubbleNode status(BubbleNode node) throws Exception;

    @Override default boolean test () { return true; }

}
