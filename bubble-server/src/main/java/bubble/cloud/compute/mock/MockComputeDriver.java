/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.compute.mock;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.cloud.compute.ComputeServiceDriverBase;
import bubble.cloud.geoLocation.mock.MockGeoLocationDriver;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import lombok.Getter;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

public class MockComputeDriver extends ComputeServiceDriverBase {

    private Map<String, BubbleNode> nodes = new ConcurrentHashMap<>();

    @Getter private final List<CloudRegion> regions = singletonList(new CloudRegion()
            .setDescription("New York City (mock)")
            .setName("nyc_mock")
            .setLocation(MockGeoLocationDriver.MOCK_LOCAION));

    @Getter private final List<ComputeNodeSize> sizes = singletonList(new ComputeNodeSize()
            .setName("standard")
            .setType(ComputeNodeSizeType.small));

    @Override protected String readSshKeyId(HttpResponseBean keyResponse) { return "dummy_ssh_key_id_"+now(); }

    @Override public String registerSshKey(BubbleNode node) { return readSshKeyId(null); }

    @Override protected HttpRequestBean registerSshKeyRequest(BubbleNode node) { return null; }

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

}
