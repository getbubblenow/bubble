/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.RegionalServiceDriver;
import org.cobbzilla.util.system.CommandResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ComputeServiceDriver extends CloudServiceDriver, RegionalServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.compute; }

    List<ComputeNodeSize> getSizes();
    ComputeNodeSize getSize(ComputeNodeSizeType type);
    OsImage getOs();

    default Map<String, ComputeNodeSize> getSizesMap () {
        return getSizes().stream().collect(Collectors.toMap(ComputeNodeSize::getTypeName, Function.identity()));
    }

    BubbleNode start(BubbleNode node) throws Exception;
    BubbleNode cleanupStart(BubbleNode node) throws Exception;
    BubbleNode stop(BubbleNode node) throws Exception;
    BubbleNode status(BubbleNode node) throws Exception;

    @Override default boolean test () { return true; }

    List<PackerImage> getAllPackerImages();
    List<PackerImage> getPackerImagesForRegion(String region);
    default List<PackerImage> finalizeIncompletePackerRun(CommandResult commandResult, AnsibleInstallType installType) { return null; }

    default Map<String, Object> getPackerRegionContext(CloudRegion region) { return null; }

    default int getPackerParallelBuilds() { return 1; }

}
