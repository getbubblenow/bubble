/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.server.listener;

import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SelfNodeService;
import bubble.service.cloud.NetworkMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import java.io.File;
import java.util.Map;

import static bubble.service.boot.StandardSelfNodeService.SELF_NODE_JSON;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss;

@Slf4j
public class NodeInitializerListener extends RestServerLifecycleListenerBase<BubbleConfiguration> {

    private static final int MIN_WORKER_THREADS = 16;

    @Override public void beforeStart(RestServer server) {
        final BubbleConfiguration c = (BubbleConfiguration) server.getConfiguration();

        // ensure we can reference our own jar file
        final File bubbleJar = c.getBubbleJar();
        if (bubbleJar == null || !bubbleJar.exists()) die("beforeStart: bubble jar file not found: "+abs(bubbleJar));

        // ensure locales were loaded correctly
        final String[] allLocales = c.getAllLocales();
        if (empty(allLocales)) die("beforeStart: no locales found");  // should never happen

        // if we are using the 'http' localNotificationStrategy, ensure we have enough worker threads
        if (!c.getHttp().hasWorkerThreads() || c.getHttp().getWorkerThreads() < MIN_WORKER_THREADS) {
            log.info("beforeStart: http.workerThreads="+c.getHttp().getWorkerThreads()+" is not set or too low, increasing to "+MIN_WORKER_THREADS);
            c.getHttp().setWorkerThreads(MIN_WORKER_THREADS);
        } else {
            log.info("beforeStart: http.workerThreads="+c.getHttp().getWorkerThreads());
        }
    }

    @Override public void onStart(RestServer server) {
        final BubbleConfiguration c = (BubbleConfiguration) server.getConfiguration();

        // ensure all search views can be created
//        disabled for now, slows down startup time and consumes much memory.
//        Most search views will probably not need to be instantiated anyway
//        if (!c.testMode()) {
//            c.getAllDAOs().stream()
//                    .filter(dao -> dao instanceof AbstractDAO)
//                    .forEach(dao -> ((AbstractDAO) dao).getSearchView());
//        }

        // ensure system configs can be loaded properly
        final Map<String, Object> configs = c.getPublicSystemConfigs();
        if (empty(configs)) die("onStart: no system configs found");  // should never happen

        final AccountDAO accountDAO = c.getBean(AccountDAO.class);
        if (!accountDAO.activated()) {
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
            c.getBean(SelfNodeService.class).initThisNode(thisNode);
        } else {
            log.warn("onStart: thisNode was null, not doing standard initializations");
        }

        // start network monitor if we manage networks
        if (c.isSageLauncher()) c.getBean(NetworkMonitorService.class).start();

        // warm up drivers
        final Account admin = accountDAO.getFirstAdmin();
        if (admin != null) {
            for (CloudService cloud : c.getBean(CloudServiceDAO.class).findPublicTemplates(admin.getUuid())) {
                try {
                    cloud.wireAndSetup(c);
                } catch (Exception e) {
                    die("onStart: error initializing driver for cloud: "+cloud.getName()+"/"+cloud.getUuid()+": "+shortError(e), e);
                }
//            background(() -> cloud.wireAndSetup(c));
            }
        }

        // ensure default devices exist
        if (thisNode != null) {
            final BubbleNetwork thisNetwork = c.getThisNetwork();
            if (thisNetwork != null && thisNetwork.getInstallType() == AnsibleInstallType.node) {
                for (Account a : accountDAO.findAll()) {
                    c.getBean(DeviceDAO.class).ensureSpareDevice(a.getUuid(), thisNode.getNetwork(), false);
                }
            }
        }

        log.info("onStart: completed");
    }

}
