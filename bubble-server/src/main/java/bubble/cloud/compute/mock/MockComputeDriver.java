/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.mock;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.*;
import bubble.cloud.geoLocation.mock.MockGeoLocationDriver;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;

public class MockComputeDriver extends ComputeServiceDriverBase {

    private Map<String, BubbleNode> nodes = new ConcurrentHashMap<>();

    @Getter private final List<CloudRegion> cloudRegions = singletonList(new CloudRegion()
            .setDescription("New York City (mock)")
            .setName("nyc_mock")
            .setLocation(MockGeoLocationDriver.MOCK_LOCAION));

    @Getter private final List<ComputeNodeSize> cloudSizes = singletonList(new ComputeNodeSize()
            .setName("standard")
            .setType(ComputeNodeSizeType.small));

    @Getter private final List<OsImage> cloudOsImages = singletonList(new OsImage().setName("dummy operating system"));

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        node.setIp4("127.0.0.1");
        node.setIp6("::1");
        nodes.put(node.getUuid(), node);
        return node.setState(BubbleNodeState.running);
    }

    @Override public List<BubbleNode> listNodes() throws IOException {
        return new ArrayList<>(nodes.values());
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception { return node; }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        final BubbleNode found = nodes.get(node.getUuid());
        if (found == null) return null;
        nodes.put(node.getUuid(), node.setState(BubbleNodeState.stopped));
        return node;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final BubbleNode found = nodes.get(node.getUuid());
        if (found == null) return node.setState(BubbleNodeState.unknown_error);;
        nodes.put(node.getUuid(), node);
        return node;
    }

    @Override public List<PackerImage> getAllPackerImages() { return Collections.emptyList(); }
    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return Collections.emptyList(); }

}
