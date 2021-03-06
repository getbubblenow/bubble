/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.local;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.compute.*;
import bubble.model.cloud.BubbleNode;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public class LocalComputeDriver extends CloudServiceDriverBase<ComputeConfig> implements ComputeServiceDriver {

    @Override public List<ComputeNodeSize> getSizes() { return notSupported("getSizes"); }
    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) { return notSupported("getSize"); }
    @Override public OsImage getOs() { return notSupported("getOs"); }

    @Override public List<CloudRegion> getRegions() { return notSupported("getRegions"); }
    @Override public CloudRegion getRegion(String region) { return notSupported("getRegion"); }

    @Override public BubbleNode start(BubbleNode node) throws Exception { return notSupported("start"); }
    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception { return notSupported("cleanupStart"); }
    @Override public BubbleNode stop(BubbleNode node) throws Exception { return notSupported("stop"); }
    @Override public BubbleNode status(BubbleNode node) throws Exception { return notSupported("status"); }

    @Override public List<PackerImage> getAllPackerImages() { return notSupported("getPackerImages"); }
    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return notSupported("getPackerImagesForRegion"); }

}
