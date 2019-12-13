package bubble.server.listener;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SageHelloService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static bubble.server.BubbleServer.isRestoreMode;
import static bubble.service.boot.StandardSelfNodeService.SELF_NODE_JSON;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss;

@Slf4j
public class NodeInitializerListener extends RestServerLifecycleListenerBase<BubbleConfiguration> {

    @Override public void onStart(RestServer server) {

        final BubbleConfiguration c = (BubbleConfiguration) server.getConfiguration();
        if (!c.getBean(AccountDAO.class).activated()) {
            final File nodeFile = THIS_NODE_FILE;
            if (nodeFile.exists()) {
                final File backupSelfNode = new File(abs(nodeFile) + ".backup_" + DATE_FORMAT_YYYY_MM_DD_HH_mm_ss.print(now()));
                if (!nodeFile.renameTo(backupSelfNode)) {
                    die("onStart: error renaming "+SELF_NODE_JSON+" -> "+abs(backupSelfNode)+" for un-activated bubble");
                }
            }
            log.info("onStart: renamed "+SELF_NODE_JSON+" for un-activated bubble");
        }

        final BubbleNode thisNode = c.getThisNode();
        if (thisNode != null) {
            initThisNode(c, thisNode);
        } else {
            log.warn("onStart: thisNode was null, not doing standard initializations");
        }
    }

    private boolean initThisNode(BubbleConfiguration c, BubbleNode thisNode) {
        final BubbleNodeDAO nodeDAO = c.getBean(BubbleNodeDAO.class);

        final BubbleNode dbThis = nodeDAO.findByUuid(thisNode.getUuid());
        if (dbThis == null) return die("initThisNode: self_node not found in database: "+thisNode.getUuid());

        // check database, ip4/ip6 may not have been set for ourselves. let's set them now
        if (!dbThis.hasIp4()) {
            log.info("initThisNode: updating ip4 for self_node in database: "+thisNode.id());
            dbThis.setIp4(thisNode.getIp4());
        } else if (thisNode.hasIp4() && !dbThis.getIp4().equals(thisNode.getIp4())) {
            log.warn("initThisNode: self_node ("+thisNode.getIp4()+") and database row ("+dbThis.getIp4()+") have differing ip4 addresses for node "+thisNode.getUuid());
            dbThis.setIp4(thisNode.getIp4());
        }

        if (!dbThis.hasIp6()) {
            log.info("initThisNode: updating ip6 for self_node in database: "+thisNode.id());
            dbThis.setIp6(thisNode.getIp6());
        } else if (thisNode.hasIp6() && !dbThis.getIp6().equals(thisNode.getIp6())) {
            log.warn("initThisNode: self_node ("+thisNode.getIp6()+") and database row ("+dbThis.getIp6()+") have differing ip6 addresses for node "+thisNode.getUuid());
            dbThis.setIp6(thisNode.getIp6());
        }
        nodeDAO.update(dbThis);

        // ensure a token exists so we can call ourselves
        final BubbleNodeKeyDAO keyDAO = c.getBean(BubbleNodeKeyDAO.class);
        final List<BubbleNodeKey> keys = keyDAO.findByNode(thisNode.getUuid());
        if (BubbleNodeKey.shouldGenerateNewKey(keys)) {
            keyDAO.create(new BubbleNodeKey(thisNode));
        }

        if (!isRestoreMode()) {
            final CloudServiceDAO cloudDAO = c.getBean(CloudServiceDAO.class);
            final String network = thisNode.getNetwork();

            // ensure storage delegates use a network-specific key
            final List<String> updatedClouds = new ArrayList<>();
            cloudDAO.findByType(CloudServiceType.storage).stream()
                    .filter(cloud -> cloud.getCredentials() != null
                            && cloud.getCredentials().needsNewNetworkKey(network)
                            && !cloud.getDriverClass().equals(LocalStorageDriver.class.getName()))
                    .forEach(cloud -> {
                            cloudDAO.update(cloud.setCredentials(cloud.getCredentials().initNetworkKey(network)));
                            log.info("onStart: set network-specific key for storage: " + cloud.getName());
                            updatedClouds.add(cloud.getName() + "/" + cloud.getUuid());
                    });
            if (!updatedClouds.isEmpty()) {
                log.info("onStart: updated network-specific keys for storage clouds: " + StringUtil.toString(updatedClouds));
            }
        }

        // start hello sage service, if we have a sage that is not ourselves
        if (c.hasSageNode() && !c.isSelfSage()) {
            log.info("onStart: starting SageHelloService");
            c.getBean(SageHelloService.class).start();
        }

        return true;
    }
}
