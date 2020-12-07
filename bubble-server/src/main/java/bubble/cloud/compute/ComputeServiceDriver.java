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
import bubble.service.packer.PackerBuild;
import org.cobbzilla.util.io.TempDir;
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

    default boolean supportsPacker(AnsibleInstallType installType) { return true; }

    default CloudRegion[] getRegions(PackerBuild packerBuild) {
        final String[] parts = packerBuild.getArtifact_id().split(":");
        final String[] regionNames = parts[0].split(",");
        final CloudRegion[] regions = new CloudRegion[regionNames.length];
        for (int i=0; i<regionNames.length; i++) {
            regions[i] = new CloudRegion().setInternalName(regionNames[i]);
        }
        return regions;
    }

    default String getPackerImageId(String name, PackerBuild packerBuild) {
        final String[] parts = packerBuild.getArtifact_id().split(":");
        return parts[1];
    }

    default boolean supportsDns() { return true; }

    default int getSshPort(BubbleNode node) { return 1202; }

    default void prepPackerDir(TempDir tempDir) {}

}
