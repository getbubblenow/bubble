/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.upgrade;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.notify.upgrade.AppsUpgradeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SageHelloService;
import bubble.service.notify.NotificationService;
import bubble.service.stream.StandardRuleEngineService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static bubble.model.cloud.notify.NotificationType.upgrade_apps_request;
import static bubble.service.upgrade.AppObjectUpgradeHandler.APP_UPGRADE_HANDLERS;
import static bubble.service.upgrade.AppObjectUpgradeHandler.APP_UPGRADE_HANDLERS_REVERSED;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_ASC;

@Service @Slf4j
public class AppUpgradeService extends SimpleDaemon {

    @Getter private final long sleepTime = DAYS.toMillis(1);

    @Autowired private BubbleConfiguration configuration;
    @Autowired private NotificationService notificationService;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private SageHelloService helloService;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private StandardRuleEngineService ruleEngine;

    @Override protected void process() {
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null) {
            log.warn("process: thisNetwork is null, not running");
            return;
        }

        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) {
            log.warn("process: thisNode is null, not running");
            return;
        }

        final BubbleNode sageNode = configuration.getSageNode();
        if (sageNode == null) {
            log.warn("process: sageNode is null, not running");
            return;
        }

        // excluding sage, are we the oldest running node?
        final List<BubbleNode> nodes = nodeDAO.findRunningByNetwork(thisNetwork.getUuid()).stream()
                .filter(n -> !n.getUuid().equals(sageNode.getUuid()))
                .collect(Collectors.toList());
        if (empty(nodes)) {
            log.warn("process: no nodes found for thisNetwork="+thisNetwork.getName()+", not running");
            return;
        }

        // only run if we are the oldest running node in the network
        Collections.sort(nodes, CTIME_ASC);
        final BubbleNode oldestRunningNode = nodes.get(0);
        if (!oldestRunningNode.getUuid().equals(thisNode.getUuid())) {
            log.warn("process: thisNode ("+thisNode.id()+") is not the oldest running node (which is "+oldestRunningNode.id()+"), not running");
            return;
        }

        // ensure we have sent a hello to the sage
        if (!helloService.sageHelloSuccessful()) {
            log.warn("process: SageHelloService.sageHelloSuccessful returned false, not running");
            return;
        }

        // ensure we are running the same version as the sage
        if (!configuration.hasSageVersion()) {
            log.warn("process: SageVersion not set, not running");
            return;
        }
        if (!configuration.sameVersionAsSage()) {
            log.warn("process: SageVersion ("+configuration.getSageVersion().getVersion()+") does not match our version ("+configuration.getVersionInfo().getVersion()+"), not running");
            return;
        }

        // first run for oldest admin account, they will determine if anything is pulled in
        final Account admin = accountDAO.getFirstAdmin();
        if (admin == null) {
            log.warn("process: no first admin account, not running");
            return;
        }

        if (!admin.wantsAppUpdates()) {
            log.warn("process: admin has automatic updates disabled, not running");
            return;
        }

        handleAdminUpgrades(admin, sageNode);
    }

    private void handleAdminUpgrades(Account admin, BubbleNode sageNode) {
        try {
            final AppsUpgradeNotification upgradeRequest = buildUpgradeNotification(admin);
            log.info("handleAdminUpgrades: sending upgrade request to sage: "+upgradeRequest);

            final AppsUpgradeNotification upgradeResponse = notificationService.notifySync(sageNode, upgrade_apps_request, upgradeRequest);
            log.info("handleAdminUpgrades: received upgrade response from sage: "+upgradeResponse);

            final List<Account> accounts = accountDAO.findNotDeleted().stream()
                    .filter(a -> !a.suspended() && !a.getUuid().equals(admin.getUuid()))
                    .collect(Collectors.toList());
            log.info("handleAdminUpgrades: found "+accounts.size()+" accounts");

            final Map<String, List<RuleDriver>> accountDrivers = new HashMap<>();

            // update rule drivers first, some AppRules may need these
            log.info("handleAdminUpgrades: loading sage drivers");
            final RuleDriver[] sageDrivers = upgradeResponse.getDrivers();
            log.info("handleAdminUpgrades: loaded "+sageDrivers.length+" sage drivers");

            final List<RuleDriver> myDrivers = Arrays.asList(upgradeRequest.getDrivers());
            accountDrivers.put(admin.getUuid(), myDrivers);

            ruleEngine.disableCacheFlushing();
            for (RuleDriver sageDriver : sageDrivers) {
                log.info("handleAdminUpgrades: updating admin driver: "+sageDriver.getName());
                updateDriver(admin, myDrivers, sageDriver);

                for (Account account : accounts) {
                    if (account.wantsAppUpdates()) {
                        final List<RuleDriver> drivers = driverDAO.findByAccount(account.getUuid());
                        log.info("handleAdminUpgrades: updating account driver: "+sageDriver.getName());
                        updateDriver(account, drivers, sageDriver);
                        accountDrivers.put(account.getUuid(), drivers);
                    }
                }
            }

            final BubbleApp[] sageApps = upgradeResponse.getApps();
            for (BubbleApp sageApp : sageApps) {
                updateApp(admin, accounts, sageDrivers, accountDrivers, sageApp);
            }
        } catch (Exception e) {
            log.error("handleAdminUpgrades: "+shortError(e), e);

        } finally {
            ruleEngine.enableCacheFlushing();
        }
    }

    private AppsUpgradeNotification buildUpgradeNotification(Account admin) {
        final AppsUpgradeNotification notification = new AppsUpgradeNotification();
        for (RuleDriver driver : driverDAO.findByAccount(admin.getUuid())) {
            notification.addDriver(driver);
        }
        for (BubbleApp app : appDAO.findByAccount(admin.getUuid())) {
            notification.addApp(app);
        }
        return notification;
    }

    private void updateDriver(Account account, List<RuleDriver> myDrivers, RuleDriver sageDriver) {
        RuleDriver myDriver = myDrivers.stream()
                .filter(d -> d.getName().equals(sageDriver.getName()))
                .findFirst()
                .orElse(null);
        final RuleDriver sageCopy = copy(sageDriver);
        if (myDriver == null) {
            sageCopy.setAccount(account.getUuid());
            sageCopy.setTemplate(sageCopy.template() && account.admin());
            log.info("updateDriver: creating RuleDriver: "+sageCopy.getName());
            myDrivers.add(driverDAO.create(sageCopy));
        } else {
            // preserve existing "enabled" flag
            boolean enabled = myDriver.enabled();
            myDriver.update(sageCopy);
            myDriver.setEnabled(enabled);
            log.info("updateDriver: updating RuleDriver: "+sageCopy.getName());
            driverDAO.update(myDriver);
        }
    }

    private void updateApp(Account admin,
                           List<Account> accounts,
                           RuleDriver[] sageDrivers,
                           Map<String, List<RuleDriver>> accountDrivers,
                           BubbleApp sageApp) throws Exception {
        updateApp(admin, admin, sageApp, sageDrivers, accountDrivers);
        for (Account account : accounts) {
            if (account.wantsAppUpdates()) {
                updateApp(admin, account, sageApp, sageDrivers, accountDrivers);
            }
        }
    }

    private void updateApp(Account admin,
                           Account account,
                           BubbleApp sageApp,
                           RuleDriver[] sageDrivers,
                           Map<String, List<RuleDriver>> accountDrivers) throws Exception {
        final List<BubbleApp> myApps = appDAO.findByAccount(account.getUuid());
        BubbleApp myApp = myApps.stream()
                .filter(a -> a.getName().equals(sageApp.getName()))
                .findFirst()
                .orElse(null);
        final BubbleApp sageCopy = copy(sageApp);
        if (myApp == null) {
            sageCopy.setAccount(account.getUuid());
            sageCopy.setTemplate(sageApp.template() && account.getUuid().equals(admin.getUuid()));
            log.info("updateApp: creating BubbleApp: "+sageCopy.getName());
            myApp = appDAO.create(sageCopy);
        } else {
            // preserve existing "enabled" flag
            boolean enabled = myApp.enabled();
            myApp.update(sageCopy);
            myApp.setEnabled(enabled);
            log.info("updateApp: updating BubbleApp: "+sageCopy.getName());
            myApp = appDAO.update(myApp);
        }

        for (AppObjectUpgradeHandler handler : APP_UPGRADE_HANDLERS) {
            handler.updateAppObjects(configuration, account, myApp, sageApp, sageDrivers, accountDrivers.get(account.getUuid()));
        }
        for (AppObjectUpgradeHandler handler : APP_UPGRADE_HANDLERS_REVERSED) {
            if (handler.shouldDelete()) {
                handler.removeAppObjects(configuration, account, myApp, sageApp, sageDrivers, accountDrivers.get(account.getUuid()));
            }
        }
    }

}
