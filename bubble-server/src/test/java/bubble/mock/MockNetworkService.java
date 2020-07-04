/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.mock;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.compute.mock.MockComputeDriver;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.*;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.NodeLaunchMonitor;
import bubble.service.cloud.StandardNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Service
public class MockNetworkService extends StandardNetworkService {

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override protected String lockNetwork(String network) { return "lock"; }
    @Override protected boolean confirmLock(String network, String lock) { return true; }
    @Override protected void unlockNetwork(String network, String lock) {}

    @Override public BubbleNode newNode(NewNodeNotification nn, NodeLaunchMonitor launchMonitor) {

        final BubbleNetwork network = networkDAO.findByUuid(nn.getNetwork());
        final CloudService cloud = findServiceOrDelegate(nn.getCloud());
        final CloudService nodeCloud = cloudDAO.findByAccountAndName(network.getAccount(), cloud.getName());
        if (nodeCloud == null) return die("newNode: node cloud not found: "+cloud.getName()+" for account "+network.getAccount());

        final ComputeServiceDriver computeDriver = cloud.getComputeDriver(configuration);
        if (!(computeDriver instanceof MockComputeDriver)) return die("newNode: expected MockComputeDriver");

        final BubbleNode node = nodeDAO.create(new BubbleNode()
                .setHost(nn.getHost())
                .setState(BubbleNodeState.running)
                .setSageNode(nn.fork() ? null : configuration.getThisNode().getUuid())
                .setNetwork(network.getUuid())
                .setInstallType(network.getInstallType())
                .setSslPort(network.getSslPort())
                .setDomain(network.getDomain())
                .setAccount(network.getAccount())
                .setSizeType(network.getComputeSizeType())
                .setSize(computeDriver.getSize(network.getComputeSizeType()).getInternalName())
                .setCloud(nodeCloud.getUuid())
                .setRegion(nn.getRegion()));

        network.setState(BubbleNetworkState.running);
        networkDAO.update(network);

        return node;
    }

    @Override public boolean stopNetwork(BubbleNetwork network) {
        network.setState(BubbleNetworkState.stopped);
        networkDAO.update(network);
        for (BubbleNode node : nodeDAO.findByNetwork(network.getUuid())) {
            nodeDAO.forceDelete(node.getUuid());
        }
        return true;
    }

    @Override public BubbleNode stopNode(BubbleNode node) {
        return node.setState(BubbleNodeState.stopped);
    }

    @Override public BubbleNode killNode(BubbleNode node, String message) {
        return node.setState(BubbleNodeState.stopped);
    }

}
