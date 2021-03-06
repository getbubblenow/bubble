/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.dbfilter;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.model.account.*;
import bubble.model.app.AppTemplateEntity;
import bubble.model.app.BubbleApp;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static bubble.cloud.NoopCloud.NOOP_CLOUD;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE_STANDARD_BASE_DIR;
import static bubble.service.dbfilter.EndOfEntityStream.END_OF_ENTITY_STREAM;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.model.NamedEntity.names;

@Slf4j
public abstract class EntityIterator implements Iterator<Identifiable> {

    private static final int MAX_QUEUE_SIZE = 100;

    private final BlockingQueue<Identifiable> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    @Getter private final Thread thread;
    @Getter private final AtomicReference<Exception> error;
    @Getter private final boolean paymentsEnabled;
    private List<BubbleApp> userApps;
    private final Map<CloudServiceType, CloudService> noopClouds = new HashMap<>();

    private final AtomicBoolean iterating = new AtomicBoolean(false);
    public boolean iterating () { return iterating.get(); }

    public EntityIterator(AtomicReference<Exception> error, boolean paymentsEnabled) {
        this.error = error;
        this.paymentsEnabled = paymentsEnabled;
        this.thread = background(this::_iterate, "EntityIterator", this.error::set);
    }

    @Override public boolean hasNext() {
        checkError();
        return !(queue.peek() instanceof EndOfEntityStream);
    }

    private final Map<Long, Thread> nextWaiters = new ConcurrentHashMap<>(1);

    @Override public Identifiable next() {
        checkError();
        if (!hasNext()) {
            throw new NoSuchElementException("iterator has been exhausted");
        }
        long threadId = Thread.currentThread().getId();
        try {
            nextWaiters.computeIfAbsent(threadId, id -> Thread.currentThread());
            return queue.take();
        } catch (InterruptedException e) {
            error.set(e);
            return die("next: queue.take interrupted");
        } finally {
            nextWaiters.remove(threadId);
        }
    }

    private void _iterate() {
        try {
            iterating.set(true);
            iterate();
            add(END_OF_ENTITY_STREAM);
        } catch (Exception e) {
            error.set(e);
            nextWaiters.values().forEach(Thread::interrupt);
            die("_iterate: "+shortError(e), e);
        } finally {
            iterating.set(false);
        }
    }

    protected abstract void iterate();

    protected void add(Identifiable from) {
        if (log.isDebugEnabled()) log.debug("add: "+from.getClass().getSimpleName()+"/"+from.getUuid());
        try {
            queue.put(from);
        } catch (InterruptedException e) {
            error.set(e);
            die("add: queue.put interrupted");
        }
    }

    public void addEntities(boolean fullCopy,
                            Class<? extends Identifiable> c,
                            List<? extends Identifiable> entities,
                            BubbleNetwork network,
                            BubbleNode node,
                            List<BubblePlanApp> planApps) {
        if (CloudService.class.isAssignableFrom(c)) {
            entities.stream()
                    .filter(cloud -> fullCopy || notPaymentCloud((CloudService) cloud))
                    .forEach(e -> {
                        final CloudService cs = (CloudService) e;
                        if (!fullCopy) {
                            if (cs.getDriverClass().equals(NOOP_CLOUD)) {
                                final CloudServiceType type = cs.getType();
                                if (noopClouds.containsKey(type)) {
                                    if (log.isWarnEnabled()) log.warn("addEntities: multiple " + NOOP_CLOUD + " drivers found for type=" + type);
                                } else {
                                    noopClouds.put(type, cs);
                                }
                            } else {
                                // for new node, cloud services become templates
                                cs.setTemplate(true);
                            }
                        }
                        add(setLocalStoragePath(cs));
                    });

        } else if (!fullCopy && BubbleNetwork.class.isAssignableFrom(c)) {
            // only add the network for the node we are starting
            entities.stream()
                    .filter(e -> e.getUuid().equals(network.getUuid()))
                    .forEach(this::add);

        } else if (!fullCopy && AccountPlan.class.isAssignableFrom(c)) {
            // only add the plan for the node we are starting
            entities.stream()
                    .filter(e -> network.getUuid().equals(((AccountPlan) e).getNetwork()))
                    .forEach(this::add);

        } else if (!fullCopy && BubbleBackup.class.isAssignableFrom(c)) {
            // only add backups for the node we are starting
            entities.stream()
                    .filter(e -> network.getUuid().equals(((BubbleBackup) e).getNetwork()))
                    .forEach(this::add);

        } else if (Account.class.isAssignableFrom(c)) {
            entities.forEach(e -> {
                if (network != null && network.hasAdminEmail() && network.getAccount().equals(e.getUuid())) {
                    final Account a = (Account) e;
                    a.setEmail(network.getAdminEmail());
                }
                add(((Account) e).setPreferredPlan(null));
            });

        } else if (AccountPolicy.class.isAssignableFrom(c) && network != null && network.hasAdminEmail()) {
            entities.forEach(e -> {
                if (network.hasAdminEmail()) {
                    final AccountPolicy p = (AccountPolicy) e;
                    if (p.getAccount().equals(network.getAccount())) {
                        final AccountContact adminContact = new AccountContact()
                                .setType(CloudServiceType.email)
                                .setInfo(network.getAdminEmail())
                                .setVerified(true)
                                .setRemovable(false);
                        p.setAccountContacts(new AccountContact[]{adminContact});
                    }
                }
                add(e);
            });

        } else if (AccountSshKey.class.isAssignableFrom(c)) {
            entities.forEach(e -> add(setInstallKey((AccountSshKey) e, network)));

        } else if (!fullCopy && AccountPaymentMethod.class.isAssignableFrom(c)) {
            // clear out payment information, set driver to noop
            final CloudService noopCloud = noopClouds.get(CloudServiceType.payment);
            if (noopCloud == null) {
                if (paymentsEnabled) die("addEntities: "+NOOP_CLOUD+" for payment cloud type not found");
            } else {
                entities.forEach(e -> {
                    final AccountPaymentMethod apm = (AccountPaymentMethod) e;
                    apm.setMaskedPaymentInfo("")
                            .setPaymentInfo("")
                            .setCloud(noopCloud.getUuid())
                            .setDeleted(now())
                            .setPromotion(null);
                    add(apm);
                });
            }

        } else if (!fullCopy && planApps != null && BubbleApp.class.isAssignableFrom(c)) {
            // only copy enabled apps, make them templates
            if (log.isDebugEnabled()) log.debug("addEntities: starting with planApps="+json(planApps.stream().map(BubblePlanApp::getApp).collect(Collectors.toList())));
            userApps = new ArrayList<>();
            entities.stream().filter(app -> planAppEnabled(((BubbleApp) app).getTemplateAppOrSelf(), planApps))
                    .map(app -> ((BubbleApp) app).setTemplate(true))
                    .forEach(app -> {
                        userApps.add(app);  // save these for later, we will need them when copying BubblePlanApps below
                        add(app);
                    });
            if (log.isDebugEnabled()) log.debug("addEntities: set userApps="+json(userApps.stream().map(BubbleApp::getName).collect(Collectors.toList())));

        } else if (!fullCopy && planApps != null && BubblePlanApp.class.isAssignableFrom(c)) {
            // the only BubblePlanApps we will see here are the ones associated with the system BubblePlans
            // and the system/template BubbleApps.
            // But for this new node, the BubbleApps that are associated with the first user (admin of new node)
            // will become the new system/template apps.
            // So we rewrite the "app" field to refer to the BubbleApp owned by the user.

            // Unless for some odd reason we are deploying a node with NO apps, in which case we can skip this section entirely
            if (planApps.isEmpty()) {
                if (log.isWarnEnabled()) log.warn("addEntities: no BubblePlanApps enabled, none will be copied to new node");

            } else {
                for (Identifiable e : entities) {
                    final BubblePlanApp systemPlanApp = (BubblePlanApp) e;
                    final BubbleApp userApp = userApps.stream()
                            .filter(app -> app.getTemplateAppOrSelf().equals(systemPlanApp.getApp()))
                            .findFirst().orElse(null);
                    if (userApp == null) {
                        if (log.isInfoEnabled()) log.info("addEntities: system BubblePlanApp " + systemPlanApp.getUuid() + ": no matching BubbleApp found in userApps (not adding): " + names(userApps));
                    } else {
                        // systemPlanApp will now be associated with "root"'s BubblePlan, but user's BubbleApp
                        if (log.isInfoEnabled()) log.info("addEntities: rewrote app for " + systemPlanApp.getUuid() + " -> " + userApp.getName() + "/" + userApp.getUuid());
                        add(systemPlanApp.setApp(userApp.getUuid()));
                    }
                }
            }

        } else if (!fullCopy && planApps != null && AppTemplateEntity.class.isAssignableFrom(c)) {
            // Only copy app-related objects if the corresponding app is among the planApps
            // Make copied objects templates
            for (AppTemplateEntity e : (List<? extends AppTemplateEntity>) entities) {
                if (userAppEnabled(e.getApp(), userApps)) {
                    if (log.isDebugEnabled()) log.debug("addEntities: adding " + c.getSimpleName() + "/" + e.getUuid() + " (app="+e.getApp()+") as template");
                    add(e.setTemplate(true));
                } else {
                    log.debug("addEntities: NOT adding " + c.getSimpleName() + "/" + e.getUuid() + " (app="+e.getApp()+"), app not enabled (planApps="+planApps.stream().map(IdentifiableBase::getUuid).collect(Collectors.joining(", "))+")");
                }
            }

        } else if (!fullCopy && planApps != null && AccountTemplate.class.isAssignableFrom(c)) {
            // only copy app-related entities for enabled apps, make them all templates
            entities.stream()
                    .map(app -> (AccountTemplate) ((AccountTemplate) app).setTemplate(true))
                    .forEach(this::add);

        } else {
            entities.forEach(this::add);
        }
    }

    private boolean notPaymentCloud(CloudService cloud) { return !cloud.getDriverClass().contains(".payment."); }

    private boolean planAppEnabled(String appUuid, List<BubblePlanApp> planApps) {
        return planApps == null || planApps.stream().anyMatch(planApp -> planApp.getApp().equals(appUuid));
    }

    private boolean userAppEnabled(String appUuid, List<BubbleApp> userApps) {
        return userApps == null || userApps.stream().anyMatch(app -> app.getUuid().equals(appUuid));
    }

    private AccountSshKey setInstallKey(AccountSshKey sshKey, BubbleNetwork network) {
        if (network == null) return sshKey;
        if (network.hasSshKey() && network.getSshKey().equals(sshKey.getUuid())) {
            if (log.isInfoEnabled()) log.info("setInstallKey: setting install=true for key="+sshKey.getName()+"/"+sshKey.getUuid());
            sshKey.setInstallSshKey(true);
        } else {
            if (log.isInfoEnabled()) log.info("setInstallKey: NOT setting install=true for key="+sshKey.getName()+"/"+sshKey.getUuid());
        }
        return sshKey;
    }

    private CloudService setLocalStoragePath(CloudService cloudService) {
        if (!cloudService.usesDriver(LocalStorageDriver.class)) {
            return cloudService;
        }
        final LocalStorageConfig localConfig = json(cloudService.getDriverConfigJson(), LocalStorageConfig.class);
        return cloudService.setDriverConfigJson(json(localConfig.setBaseDir(LOCAL_STORAGE_STANDARD_BASE_DIR)));
    }

    private void checkError() {
        final Exception ex = error.get();
        if (ex != null) {
            if (ex instanceof RuntimeException) throw (RuntimeException) ex;
            die(getClass().getName()+": "+shortError(ex));
        }
    }

}
