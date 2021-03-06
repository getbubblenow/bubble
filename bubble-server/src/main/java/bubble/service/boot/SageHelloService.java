/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleVersionInfo;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.notify.upgrade.JarUpgradeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.notify.NotificationService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.AUTH_ENDPOINT;
import static bubble.ApiConstants.EP_UPGRADE;
import static bubble.model.cloud.notify.NotificationType.hello_to_sage;
import static bubble.model.cloud.notify.NotificationType.upgrade_request;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;

@Service @Slf4j
public class SageHelloService extends JarUpgradeMonitor {

    public static final long HELLO_SAGE_INTERVAL = HOURS.toMillis(6);
    public static final long HELLO_SAGE_START_DELAY = SECONDS.toMillis(10);

    @Override protected long getStartupDelay() { return HELLO_SAGE_START_DELAY; }
    @Override protected long getSleepTime() { return HELLO_SAGE_INTERVAL; }
    @Override protected boolean canInterruptSleep() { return true; }

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private StandardSelfNodeService selfNodeService;
    @Autowired private NotificationService notificationService;
    @Autowired private NodeManagerService nodeManagerService;

    private final AtomicBoolean sageHelloSent = new AtomicBoolean(false);
    public boolean sageHelloSuccessful () { return sageHelloSent.get(); }

    private final AtomicReference<AccountMessage> unlockMessage = new AtomicReference<>();
    public void setUnlockMessage(AccountMessage message) {
        synchronized (unlockMessage) {
            if (unlockMessage.get() != null) {
                log.warn("setUnlockMessage: message already set");
            } else {
                unlockMessage.set(message);
            }
        }
    }

    @Override protected void process() {
        final BubbleConfiguration c = configuration;
        final BubbleNode selfNode = c.getThisNode();
        if (selfNode != null && c.hasSageNode() && !selfNode.getUuid().equals(selfNode.getSageNode())) {
            final BubbleNode sage = nodeDAO.findByUuid(selfNodeService.getSageNode().getUuid());
            if (sage == null) {
                log.error("hello_to_sage: sage node not found: " + c.getSageNode());
            } else {
                // If we do not have a nodemanager password, generate one now and include it with our hello
                selfNode.setNodeManagerPassword(nodeManagerService.generatePasswordOrNull());

                log.info("hello_to_sage: sending hello...");
                final NotificationReceipt receipt = notificationService.notify(sage, hello_to_sage, selfNode);
                log.info("hello_to_sage: received reply from sage node: " + json(receipt, COMPACT_MAPPER));
                if (receipt != null && receipt.isSuccess()) {
                    if (!sageHelloSent.get()) sageHelloSent.set(true);
                    synchronized (unlockMessage) {
                        if (unlockMessage.get() != null && !unlockMessage.get().hasUuid()) {
                            final AccountMessageDAO messageDAO = c.getBean(AccountMessageDAO.class);
                            unlockMessage.set(messageDAO.create(unlockMessage.get()));
                        }
                    }
                }
                selfNode.setNodeManagerPassword(null); // just in case the object gets sync'd to db
            }
        }
    }

    @Override public void processException(Exception e) throws Exception {
        log.error("hello_to_sage: " + e, e);
        if (getIsDone()) throw e;
        sleep(HELLO_SAGE_INTERVAL / 10, "hello_to_sage: awaiting next hello after error");
    }

    @Override public void downloadJar(File upgradeJar, BubbleVersionInfo sageVersion) {
        // ask the sage to allow us to download the upgrade
        final String key = notificationService.notifySync(configuration.getSageNode(), upgrade_request, new JarUpgradeNotification(sageVersion));
        log.info("downloadJar: received upgrade key from sage: "+key);

        // request the jar from the sage
        final String uri = AUTH_ENDPOINT + EP_UPGRADE + "/" + configuration.getThisNode().getUuid() + "/" + key;
        final String url = configuration.nodeBaseUri(configuration.getSageNode()) + uri;
        final File newJar;
        try {
            newJar = temp(".jar");
            @Cleanup final InputStream in = HttpUtil.getUrlInputStream(url);
            FileUtil.toFile(newJar, in);
        } catch (Exception e) {
            log.error("downloadJar: error downloading jar: "+shortError(e));
            return;
        }

        // move to upgrade location, should trigger upgrade monitor
        log.info("downloadJar: writing upgradeJar: "+abs(upgradeJar));
        renameOrDie(newJar, upgradeJar);
    }
}