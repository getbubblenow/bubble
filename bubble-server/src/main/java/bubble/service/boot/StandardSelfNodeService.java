package bubble.service.boot;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.model.cloud.BubbleNodeState;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.cloud.notify.NotificationType;
import bubble.server.BubbleConfiguration;
import bubble.service.bill.BillingService;
import bubble.service.bill.StandardRefundService;
import bubble.service.notify.NotificationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.AutoRefreshingReference;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.ApiConstants.NULL_NODE;
import static bubble.model.cloud.BubbleNode.nodeFromFile;
import static bubble.model.cloud.BubbleNodeKey.nodeKeyFromFile;
import static bubble.server.BubbleServer.disableRestoreMode;
import static bubble.server.BubbleServer.isRestoreMode;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.toFileOrDie;
import static org.cobbzilla.util.json.JsonUtil.*;

@Service @Slf4j
public class StandardSelfNodeService implements SelfNodeService {

    public static final String SELF_NODE_JSON = "self_node.json";
    public static final String SAGE_NODE_JSON = "sage_node.json";
    public static final String SAGE_KEY_JSON = "sage_key.json";

    public static final File THIS_NODE_FILE = new File(HOME_DIR, SELF_NODE_JSON);
    public static final File SAGE_NODE_FILE = new File(HOME_DIR, SAGE_NODE_JSON);
    public static final File SAGE_KEY_FILE = new File(HOME_DIR, SAGE_KEY_JSON);
    public static final long MIN_SAGE_KEY_TTL = MINUTES.toMillis(5);

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private BubbleConfiguration configuration;

    private static final AtomicReference<BubbleNode> thisNode = new AtomicReference<>();
    private static final AtomicReference<BubbleNode> sageNode = new AtomicReference<>();
    private static final AtomicBoolean wasRestored = new AtomicBoolean(false);

    @Override public boolean initThisNode(BubbleNode thisNode) {
        log.info("initThisNode: initializing with thisNode="+thisNode.id());
        final BubbleConfiguration c = configuration;

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
        final List<BubbleNodeKey> keys = nodeKeyDAO.findByNode(thisNode.getUuid());
        if (BubbleNodeKey.shouldGenerateNewKey(keys)) {
            nodeKeyDAO.create(new BubbleNodeKey(thisNode));
        }

        if (!isRestoreMode()) {
            final String network = thisNode.getNetwork();

            // ensure storage delegates use a network-specific key
            final List<String> updatedClouds = new ArrayList<>();
            cloudDAO.findByType(CloudServiceType.storage).stream()
                    .filter(cloud -> cloud.getCredentials() != null
                            && cloud.getCredentials().needsNewNetworkKey(network)
                            && !cloud.usesDriver(LocalStorageDriver.class))
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

        // start RefundService if payments are enabled and this is a SageLauncher
        if (c.paymentsEnabled() && c.isSageLauncher()) {
            log.info("onStart: starting BillingService and RefundService");
            c.getBean(BillingService.class).start();
            c.getBean(StandardRefundService.class).start();
        }

        return true;
    }

    public void setActivated(BubbleNode node) {
        // only called by ActivationService on a brand-new instance
        log.info("setActivated: setting thisNode="+node.id());
        synchronized (thisNode) {
            thisNode.set(node);
            toFileOrDie(THIS_NODE_FILE, json(node, FULL_MAPPER));
        }
    }

    public BubbleNode getThisNode () {
        synchronized (thisNode) {
            final BubbleNode self = thisNode.get();
            if (self == null) {
                final BubbleNode initSelf = initThisNode();
                if (initSelf == null) {
                    return die("getThisNode: error initializing selfNode, initThisNode returned null (should never happen)");
                }
                log.debug("getThisNode: setting thisNode="+initSelf.id());
                thisNode.set(initSelf);
                if (initSelf == NULL_NODE) {
                    log.warn("getThisNode: initThisNode returned NULL_NODE");
                    return null;
                }
                if (wasRestored.get()) {
                    log.debug("getThisNode: finalizing restore for "+initSelf.id());
                    return finalizeRestore(initSelf.setWasRestored(null));
                }
                log.debug("initThisNode: wasRestored=false, just returning self: "+initSelf.id());
                return initSelf;
            } else if (self == NULL_NODE) {
                log.warn("getThisNode: initThisNode returned NULL_NODE");
                return null;
            } else {
                log.debug("getThisNode: thisNode already set, returning: "+self.id());
                return self;
            }
        }
    }

    private final AutoRefreshingReference<BubbleNetwork> thisNet = new AutoRefreshingReference<>() {
        @Override public BubbleNetwork refresh() { return networkDAO.findByUuid(getThisNode().getNetwork()); }
        @Override public long getTimeout() { return DAYS.toMillis(1); }
    };
    @Override public BubbleNetwork getThisNetwork () { return thisNet.get(); }
    @Override public void refreshThisNetwork () { thisNet.flush(); }

    @Getter(lazy=true) private final String locale   = getThisNetwork().getLocale();
    @Getter(lazy=true) private final String timezone = getThisNetwork().getTimezone();

    public boolean hasSageNode() {
        final BubbleNode selfNode = getThisNode();
        return selfNode != null && selfNode.hasSageNode();
    }

    public boolean isSelfSage() {
        final BubbleNode selfNode = getThisNode();
        return selfNode != null && selfNode.selfSage();
    }

    public BubbleNode getSageNode () {
        final BubbleNode selfNode = getThisNode();
        if (selfNode == null || selfNode.equals(NULL_NODE) || !selfNode.hasSageNode()) return null;
        synchronized (sageNode) {
            final BubbleNode sage = sageNode.get();
            if (sage == null) {
                sageNode.set(initSageNode(selfNode));
                return sageNode.get() == NULL_NODE ? null : sageNode.get();
            } else if (sage == NULL_NODE) {
                return null;
            } else {
                return sage;
            }
        }
    }

    private BubbleNode initSageNode(BubbleNode selfNode) {
        final BubbleNode sage = nodeDAO.findByUuid(selfNode.getSageNode());
        if (sage == null) {
            // do we have a local file we can fall back on?
            if (!SAGE_NODE_FILE.exists()) {
                log.warn("initSageNode: DB contains no entry for selfNode.sage ("+selfNode.getSageNode()+") and "+abs(SAGE_NODE_FILE)+ " does not exist, returning null");
                return NULL_NODE;
            }
            return ensureSageKeyExists(syncSage(selfNode, nodeFromFile(SAGE_NODE_FILE)));
        }
        return ensureSageKeyExists(syncSage(selfNode, SAGE_NODE_FILE.exists()
                ? nodeFromFile(SAGE_NODE_FILE)
                : sage));
    }

    private BubbleNode ensureSageKeyExists(BubbleNode sageNode) {
        final BubbleNodeKey sageKey = initSageKey(sageNode);
        return sageKey != null
                ? sageNode
                : die("finalizeRestore: no sage key found in DB and no sage key file, cannot finalize restore. Sage = "+sageNode.id());
    }

    private BubbleNode syncSage(BubbleNode selfNode, BubbleNode sage) {
        // if the sage has a local ip4, then selfNode is the sage. should only happen if fork was done incorrectly
        if (sage.localIp4()) {
            if (selfNode.localIp4()) return die("syncSage: selfNode is local: "+selfNode.id());
            log.warn("syncSage: sage is local ("+sage.id()+"), using selfNode as sage: "+selfNode.id());
            return syncSage(selfNode, selfNode);
        }
        // sanity check -- is there an entry in the DB with this fqdn?
        final BubbleNode sageByFqdn = nodeDAO.findByFqdn(sage.getFqdn());
        if (sageByFqdn != null) {
            if (!sageByFqdn.id().equals(sage.id())) {
                // update database with local info
                log.debug("syncSage: deleting obsolete SB sage: " + sageByFqdn.id() + " and replacing with: " + sage.id());
                nodeDAO.forceDelete(sageByFqdn.getUuid());
            } else {
                log.debug("syncSage: DB sage is same, returning it");
                return sageByFqdn;
            }
        }
        return nodeDAO.create(sage);
    }

    private BubbleNode initThisNode() {
        if (!THIS_NODE_FILE.exists()) {
            log.warn("initThisNode: "+abs(THIS_NODE_FILE)+" does not exist, returning null");
            return NULL_NODE;
        }
        final BubbleNode selfNode = nodeFromFile(THIS_NODE_FILE);
        wasRestored.set(selfNode.wasRestored()); // save this, it will probably get erased when we write to DB
        log.debug("initThisNode: loaded from "+abs(THIS_NODE_FILE)+" selfNode="+selfNode.id());
        return initSelf(selfNode);
    }

    private BubbleNode initSelf(BubbleNode selfNode) {
        log.debug("initSelf: starting with selfNode="+selfNode.id());
        final BubbleNode foundByUuid = nodeDAO.findByUuid(selfNode.getUuid());
        final BubbleNode foundByFqdn = nodeDAO.findByFqdn(selfNode.getFqdn());
        if (foundByUuid == null && foundByFqdn == null) {
            // node exists in JSON but not in DB: write it to DB
            return ensureRunning(nodeDAO.create(selfNode));

        } else if (foundByUuid != null && foundByFqdn != null) {
            // we have both --- they better match!
            if (foundByFqdn.getUuid().equals(foundByUuid.getUuid())) {
                // everything is the same, this is OK
                log.debug("initSelf: db matches self, returning self: "+selfNode.id());
                return ensureRunning(selfNode);
            }
            // what? delete existing nodes in DB and set ourselves
            nodeDAO.delete(foundByUuid.getUuid());
            nodeDAO.delete(foundByFqdn.getUuid());
            return ensureRunning(nodeDAO.create(selfNode));

        } else if (foundByUuid == null) {
            // found by fqdn but not uuid, remove fqdn and add
            nodeDAO.delete(foundByFqdn.getUuid());
            return ensureRunning(nodeDAO.create(selfNode));
        } else {
            // found by uuid but not fqdn, error
            return die("initSelf: wrong FQDN (expected "+selfNode.getFqdn()+") in foundByUuid="+foundByUuid.id());
        }
    }

    private BubbleNode ensureRunning(BubbleNode selfNode) {
        return selfNode.getState() == BubbleNodeState.running
                ? selfNode
                : nodeDAO.update(selfNode.setState(BubbleNodeState.running));
    }

    private BubbleNode finalizeRestore(BubbleNode selfNode) {
        final BubbleNode sageNode = getSageNode();
        if (sageNode == null || isSelfSage()) {
            toFileOrDie(THIS_NODE_FILE, json(selfNode, FULL_MAPPER));
            disableRestoreMode();
            wasRestored.set(false);
            log.warn("finalizeRestore: restore successful but no sage to notify");
            return selfNode;
        }

        // ensure sage key exists, or we won't be able to talk to our sage
        final BubbleNodeKey sageKey = initSageKey(sageNode);
        if (sageKey == null) {
            return die("finalizeRestore: no sage key found in DB and no sage key file, cannot finalize restore. Sage = "+sageNode.id());
        }

        log.debug("finalizeRestore: Notifying sage that restore is complete: " + getSageNode());
        final NotificationReceipt receipt = notificationService.notify(selfNode.getUuid(), sageNode, NotificationType.restore_complete, selfNode);
        if (!receipt.isSuccess()) {
            return die("finalizeRestore: sage notification failed: "+json(receipt, COMPACT_MAPPER));
        } else {
            toFileOrDie(THIS_NODE_FILE, json(selfNode, FULL_MAPPER));
            disableRestoreMode();
            wasRestored.set(false);
            log.debug("finalizeRestore: sage notified, restore is fully complete");
            return selfNode;
        }
    }

    private BubbleNodeKey initSageKey(BubbleNode sageNode) {
        log.debug("initSageKey: starting with sage="+sageNode.id());
        BubbleNodeKey sageKey = nodeKeyDAO.findFirstByNode(sageNode.getUuid());
        if (sageKey != null && !sageKey.expiresInLessThan(MIN_SAGE_KEY_TTL)) {
            return sageKey;
        }

        // try key from file
        if (!SAGE_KEY_FILE.exists()) return null;
        sageKey = nodeKeyFromFile(SAGE_KEY_FILE);
        final BubbleNodeKey existingByUuid = nodeKeyDAO.findByUuid(sageKey.getUuid());
        final BubbleNodeKey existingByHash = nodeKeyDAO.findByPublicKeyHash(sageKey.getPublicKeyHash());
        if (existingByUuid == null && existingByHash == null) {
            if (sageKey.expired()) {
                return die("initSageKey: key not found in DB, but key on disk has expired: "+sageKey);
            }
            return nodeKeyDAO.create(sageKey);

        } else if (existingByUuid != null && existingByHash != null) {
            if (!existingByHash.getUuid().equals(existingByUuid.getUuid())) {
                // should never happen, but reset just in case
                if (sageKey.expired()) {
                    return die("initSageKey: key found in DB with different entries for uuid/fqdn, and key on disk has expired: "+sageKey);
                }
                nodeKeyDAO.delete(existingByUuid.getUuid());
                nodeKeyDAO.delete(existingByHash.getUuid());
                return nodeKeyDAO.create(sageKey);
            }
            nodeKeyDAO.delete(existingByUuid.getUuid());
            return nodeKeyDAO.create(sageKey);

        } else if (existingByUuid == null) {
            // we only have a sage key by hash. use that but leave the one on disk untouched
            return existingByHash;
        } else {
            // we only have a sage key by uuid. use that but leave the one on disk untouched
            return existingByUuid;
        }
    }
}
