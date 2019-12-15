package bubble.mock;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.compute.mock.MockComputeDriver;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.*;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.StandardNetworkService;
import org.cobbzilla.util.system.CommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Service
public class MockNetworkService extends StandardNetworkService {

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public CommandResult ansibleSetup(String script) throws IOException {
        return new CommandResult(0, "mock: successful", "");
    }

    @Override public BubbleNode newNode(NewNodeNotification nn) {

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

}
