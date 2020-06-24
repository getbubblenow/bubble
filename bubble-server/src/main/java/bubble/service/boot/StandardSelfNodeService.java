/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountSshKeyDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.cloud.notify.NotificationType;
import bubble.server.BubbleConfiguration;
import bubble.service.bill.BillingService;
import bubble.service.bill.StandardRefundService;
import bubble.service.notify.NotificationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.Refreshable;
import org.cobbzilla.util.http.HttpSchemes;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.util.system.OneWayFlag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.BubbleNode.nodeFromFile;
import static bubble.model.cloud.BubbleNodeKey.nodeKeyFromFile;
import static bubble.server.BubbleServer.disableRestoreMode;
import static bubble.server.BubbleServer.isRestoreMode;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.function.Predicate.not;
import static org.cobbzilla.util.daemon.ZillaRuntime.background;
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
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private BubbleConfiguration configuration;

    private static final AtomicReference<BubbleNode> thisNode = new AtomicReference<>();
    private static final AtomicReference<BubbleNode> sageNode = new AtomicReference<>();
    private static final AtomicBoolean wasRestored = new AtomicBoolean(false);

    @Override public boolean initThisNode(BubbleNode thisNode) {
        log.info("initThisNode: initializing with thisNode="+thisNode.id());
        final BubbleConfiguration c = configuration;

        // ensure ssh keys are configured
        c.getBean(AccountSshKeyDAO.class).refreshInstalledKeys();

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

        final var thisNetworkUuid = thisNode.getNetwork();
        if (!isRestoreMode()) {

            // ensure storage delegates use a network-specific key
            final List<String> updatedClouds = new ArrayList<>();
            cloudDAO.findByType(CloudServiceType.storage).stream()
                    .filter(cloud -> cloud.getCredentials() != null
                            && cloud.getCredentials().needsNewNetworkKey(thisNetworkUuid)
                            && !cloud.usesDriver(LocalStorageDriver.class))
                    .forEach(cloud -> {
                        cloudDAO.update(cloud.setCredentials(cloud.getCredentials().initNetworkKey(thisNetworkUuid)));
                        log.info("onStart: set network-specific key for storage: " + cloud.getName());
                        updatedClouds.add(cloud.getName() + "/" + cloud.getUuid());
                    });
            if (!updatedClouds.isEmpty()) {
                log.info("onStart: updated network-specific keys for storage clouds: " + StringUtil.toString(updatedClouds));
            }
        }

        // start hello sage and spare devices services, if we have a sage that is not ourselves
        if (c.hasSageNode() && !c.isSelfSage()) {
            log.info("onStart: starting SageHelloService");
            c.getBean(SageHelloService.class).start();

            log.info("onStart: building spare devices for all account that are not root account");
            background(() -> {
                if (accountDAO.findAll()
                              .stream()
                              .filter(not(Account::isRoot))
                              .map(a -> deviceDAO.ensureAllSpareDevices(a.getUuid(), thisNetworkUuid))
                              .reduce(false, Boolean::logicalOr)
                              .booleanValue()) {
                    deviceDAO.refreshVpnUsers();
                }
            });
        }

        // start RefundService if payments are enabled and this is a SageLauncher
        if (c.paymentsEnabled() && c.isSageLauncher()) {
            log.info("onStart: starting BillingService and RefundService");
            c.getBean(BillingService.class).start();
            c.getBean(StandardRefundService.class).start();
        }

        return true;
    }

    @Override public BubbleNode getSoleNode() {
        final List<BubbleNode> all = nodeDAO.findAll();
        return all.size() == 1 ? all.get(0) : null;
    }

    public void setActivated(BubbleNode node) {
        // only called by ActivationService on a brand-new instance
        log.info("setActivated: setting thisNode="+node.id());
        synchronized (thisNode) {
            thisNode.set(node);
            toFileOrDie(THIS_NODE_FILE, json(node, FULL_MAPPER));
        }
    }

    private OneWayFlag nullWarningPrinted = new OneWayFlag("nullWarningPrinted");

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
                    if (!nullWarningPrinted.check()) log.warn("getThisNode: initThisNode returned NULL_NODE");
                    return null;
                }
                if (wasRestored.get()) {
                    log.debug("getThisNode: finalizing restore for "+initSelf.id());
                    return finalizeRestore(initSelf.setWasRestored(null));
                }
                log.debug("initThisNode: wasRestored=false, just returning self: "+initSelf.id());
                return initSelf;
            } else if (self == NULL_NODE) {
                if (!nullWarningPrinted.check()) log.warn("getThisNode: initThisNode returned NULL_NODE");
                return null;
            } else {
                log.debug("getThisNode: thisNode already set, returning: "+self.id());
                return self;
            }
        }
    }

    public static final long THIS_NET_CACHE_MILLIS = DAYS.toMillis(1);
    private final Refreshable<BubbleNetwork> thisNet = new Refreshable<>("thisNet", THIS_NET_CACHE_MILLIS, this::safeGetThisNetwork);
    private BubbleNetwork safeGetThisNetwork() { return getThisNode() == null ? null : networkDAO.findByUuid(getThisNode().getNetwork()); }

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
        BubbleNode sage = nodeDAO.findByUuid(selfNode.getSageNode());
        if (sage == null) {
            // do we have a local file we can fall back on?
            if (!SAGE_NODE_FILE.exists()) {
                log.warn("initSageNode: DB contains no entry for selfNode.sage ("+selfNode.getSageNode()+") and "+abs(SAGE_NODE_FILE)+ " does not exist, returning null");
                return NULL_NODE;
            }
            sage = syncSage(selfNode, nodeFromFile(SAGE_NODE_FILE));
        }
        sage = syncSage(selfNode, SAGE_NODE_FILE.exists()
                ? nodeFromFile(SAGE_NODE_FILE)
                : sage);
        initSageKey(sage);
        return sage;
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
        final BubbleNode foundByIp4 = nodeDAO.findByIp4(selfNode.getIp4());
        if (foundByUuid == null && foundByFqdn == null && foundByIp4 == null) {
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

        } else if (foundByUuid == null && foundByIp4 == null) {
            // found by fqdn but not uuid or ip4, remove fqdn and add
            nodeDAO.delete(foundByFqdn.getUuid());
            return ensureRunning(nodeDAO.create(selfNode));

        } else if (foundByIp4 != null) {
            // OK, use the one we found by ip4
            return foundByIp4;

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
        final NotificationReceipt receipt = notificationService.notify(sageNode, NotificationType.restore_complete, selfNode);
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
        if (!SAGE_KEY_FILE.exists()) return fetchLatestSageKey(sageNode);
        sageKey = nodeKeyFromFile(SAGE_KEY_FILE);
        final BubbleNodeKey existingByUuid = nodeKeyDAO.findByUuid(sageKey.getUuid());
        final BubbleNodeKey existingByHash = nodeKeyDAO.findByPublicKeyHash(sageKey.getPublicKeyHash());
        if (existingByUuid == null && existingByHash == null) {
            if (sageKey.expired()) {
                log.warn("initSageKey: key not found in DB and key on disk has expired, re-keying: "+sageKey);
                return fetchLatestSageKey(sageNode);
            }
            return nodeKeyDAO.create(sageKey);

        } else if (existingByUuid != null && existingByHash != null) {
            if (!existingByHash.getUuid().equals(existingByUuid.getUuid())) {
                // should never happen, but reset just in case
                if (sageKey.expired()) {
                    log.warn("initSageKey: key not found in DB and key on disk has expired, re-keying: "+sageKey);
                    return fetchLatestSageKey(sageNode);
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

    private BubbleNodeKey fetchLatestSageKey(BubbleNode sageNode) {
        log.warn("fetchLatestSageKey: key found in DB with different entries for uuid/fqdn, and key on disk has expired, refreshing: "+sageNode.id());
        try {
            final String keyUrl = HttpSchemes.SCHEME_HTTPS + sageNode.getFqdn() + ":" + sageNode.getSslPort() + configuration.getHttp().getBaseUri() + AUTH_ENDPOINT + EP_KEY;
            log.info("fetchLatestSageKey: fetching sage key from: "+keyUrl);
            final String keyJson = HttpUtil.url2string(keyUrl);
            final BubbleNodeKey sageKey = json(keyJson, BubbleNodeKey.class);
            FileUtil.toFile(SAGE_KEY_FILE, keyJson);
            nodeKeyDAO.create(sageKey);
            return sageKey;
        } catch (Exception e) {
            return die("fetchLatestSageKey: error fetching/saving latest sage key: "+e, e);
        }
    }

    @Override public BubblePlan getThisPlan() {
        final BubbleNetwork network = safeGetThisNetwork();
        if (network == null) return null;
        if (network.getInstallType() != AnsibleInstallType.node) return null;
        final AccountPlan accountPlan = accountPlanDAO.findByNetwork(network.getUuid());
        if (accountPlan == null) return null;
        return planDAO.findByUuid(accountPlan.getPlan());
    }

}
