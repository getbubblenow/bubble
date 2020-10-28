/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.*;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.device.Device;
import bubble.rule.AppRuleDriver;
import bubble.server.BubbleConfiguration;
import bubble.service.device.DeviceService;
import bubble.service.device.StandardFlexRouterService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;

@Service @Slf4j
public class StandardAppPrimerService implements AppPrimerService {

    @Autowired private AccountDAO accountDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceService deviceService;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private StandardFlexRouterService flexRouterService;
    @Autowired private RedisService redis;
    @Autowired private BubbleConfiguration configuration;

    @Getter(lazy=true) private final boolean primingEnabled = initPrimingEnabled();
    private boolean initPrimingEnabled() {
        if (configuration == null) return die("initPrimingEnabled: configuration was null");
        if (configuration.testMode()) {
            log.info("initPrimingEnabled: configuration.testMode is true, not priming");
        }
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null) {
            log.info("initPrimingEnabled: thisNetwork is null, not priming");
            return false;
        }
        if (thisNetwork.notNode()) {
            log.info("initPrimingEnabled: thisNetwork is not a node, not priming");
            return false;
        }
        return true;
    }

    public void primeApps() {
        if (!isPrimingEnabled()) {
            log.info("primeApps: not enabled");
            return;
        }
        for (Account account : accountDAO.findNotDeleted()) {
            try {
                prime(account);
            } catch (Exception e) {
                log.error("primeApps("+account.getName()+"): "+shortError(e), e);
            }
        }
        log.info("primeApps: completed");
    }

    public void prime(Account account) {
        deviceService.initBlocksAndFlexRoutes(account);
        prime(account, (BubbleApp) null);
    }

    public void prime(BubbleApp app) {
        final Account account = accountDAO.findByUuid(app.getAccount());
        if (account == null) {
            log.warn("prime("+app.getName()+"): account not found: "+app.getAccount());
            return;
        }
        prime(account, app.getUuid());
    }

    public synchronized void prime(Account account, String singleAppUuid) {
        final BubbleApp singleApp = appDAO.findByAccountAndId(account.getUuid(), singleAppUuid);
        if (singleApp == null) {
            log.warn("prime("+account.getName()+", "+singleAppUuid+"): app not found: "+singleAppUuid);
            return;
        }
        prime(account, singleApp);
    }

    @Getter(lazy=true) private final ExecutorService primerThread = fixedPool(1, "StandardAppPrimerService.primerThread");

    private void prime(Account account, BubbleApp singleApp) {
        if (!isPrimingEnabled()) {
            log.info("prime: not enabled");
            return;
        }
        getPrimerThread().submit(() -> _prime(account, singleApp));
    }

    private synchronized void _prime(@NonNull final Account account, @Nullable final BubbleApp singleApp) {
        try {
            final List<Device> devices = deviceDAO.findByAccount(account.getUuid());
            if (devices.isEmpty()) return;

            final Map<String, List<String>> accountDeviceIps =
                    devices.stream()
                           .map(Device::getUuid)
                           .collect(Collectors.toMap(Function.identity(), deviceService::findIpsByDevice));

            // flex domains can only be managed by the first admin
            final Account firstAdmin = accountDAO.getFirstAdmin();
            account.setFirstAdmin(account.getUuid().equals(firstAdmin.getUuid()));

            if (singleApp != null) {
                _primeApp(account, accountDeviceIps, devices, singleApp);
            } else {
                appDAO.findByAccount(account.getUuid())
                      .stream()
                      .filter(BubbleApp::canPrime)
                      .forEach(app -> _primeApp(account, accountDeviceIps, devices, app));
            }
        } catch (Exception e) {
            die("_prime: " + shortError(e), e);
        } finally {
            log.info("_prime: completed");
        }
    }

    private void _primeApp(@NonNull final Account account, @NonNull final Map<String, List<String>> accountDeviceIps,
                           @NonNull final List<Device> devices, @NonNull final BubbleApp app) {
        log.info("_primeApp: " + app.getUuid() + "/" + app.getName());

        final List<AppRule> rules = ruleDAO.findByAccountAndApp(account.getUuid(), app.getUuid());
        final List<AppMatcher> matchers = matcherDAO.findByAccountAndApp(account.getUuid(), app.getUuid());

        boolean updateFlexRouters = false;
        Set<String> flexDomains = null;
        for (AppRule rule : rules) {
            final RuleDriver driver = driverDAO.findByUuid(rule.getDriver());
            if (driver == null) {
                log.warn("_primeApp: driver not found for app/rule "
                         + app.getName() + "/" + rule.getName() + ": " + rule.getDriver());
                continue;
            }

            // handle AppData callback registration with a basic driver
            final AppRuleDriver cbDriver = driver.getDriver();
            if (cbDriver instanceof HasAppDataCallback) {
                log.debug("_primeApp: AppRuleDriver (" + cbDriver.getClass().getSimpleName()
                          + ") implements HasAppDataCallback, registering: " + app.getUuid() + "/" + app.getName());
                final HasAppDataCallback dataCallback = (HasAppDataCallback) cbDriver;
                dataCallback.prime(account, app, configuration);
                dataDAO.registerCallback(app.getUuid(), dataCallback.createCallback(account, app, configuration));
            }

            for (final Device device : devices) {
                final Set<String> rejectDomains = new HashSet<>();
                final Set<String> blockDomains = new HashSet<>();
                final Set<String> whiteListDomains = new HashSet<>();
                final Set<String> filterDomains = new HashSet<>();
                final Set<String> flexExcludeDomains = new HashSet<>();
                final Set<String> requestHeaderModifiers = new HashSet<>();

                boolean areAllSetsEmpty = true;
                for (AppMatcher matcher : matchers) {
                    final AppRuleDriver appRuleDriver = rule.initDriver(configuration, app, driver, matcher, account, device);

                    final Set<String> rejects = appRuleDriver.getPrimedRejectDomains();
                    if (empty(rejects)) {
                        log.debug("_primeApp: no rejectDomains for device/app/rule/matcher: " + device.getName()
                                  + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                    } else {
                        rejectDomains.addAll(rejects);
                        areAllSetsEmpty = empty(rejects);
                    }

                    final Set<String> blocks = appRuleDriver.getPrimedBlockDomains();
                    if (empty(blocks)) {
                        log.debug("_primeApp: no blockDomains for device/app/rule/matcher: " + device.getName()
                                  + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                    } else {
                        blockDomains.addAll(blocks);
                        areAllSetsEmpty = areAllSetsEmpty && empty(blocks);
                    }

                    final Set<String> whiteList = appRuleDriver.getPrimedWhiteListDomains();
                    if (empty(whiteList)) {
                        log.debug("_primeApp: no whiteListDomains for device/app/rule/matcher: " + device.getName()
                                  + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                    } else {
                        whiteListDomains.addAll(whiteList);
                        areAllSetsEmpty = areAllSetsEmpty && empty(whiteList);
                    }

                    final Set<String> filters = appRuleDriver.getPrimedFilterDomains();
                    if (empty(filters)) {
                        log.debug("_primeApp: no filterDomains for device/app/rule/matcher: " + device.getName()
                                  + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                    } else {
                        filterDomains.addAll(filters);
                        areAllSetsEmpty = areAllSetsEmpty && empty(filters);
                    }

                    final Set<String> modifiers = appRuleDriver.getPrimedResponseHeaderModifiers();
                    if (empty(modifiers)) {
                        log.debug("_primeApp: no responseHeaderModifiers for device/app/rule/matcher: "
                                  + device.getName() + "/" + app.getName() + "/" + rule.getName() + "/"
                                  + matcher.getName());
                    } else {
                        requestHeaderModifiers.addAll(modifiers);
                        areAllSetsEmpty = areAllSetsEmpty && empty(modifiers);
                    }

                    if (account.isFirstAdmin() && flexDomains == null) {
                        final Set<String> flexes = appRuleDriver.getPrimedFlexDomains();
                        if (empty(flexes)) {
                            log.debug("_primeApp: no flexDomains for device/app/rule/matcher: " + device.getName()
                                      + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                        } else {
                            flexDomains = new HashSet<>(flexes);
                            areAllSetsEmpty = areAllSetsEmpty && empty(flexes);
                        }

                        final Set<String> flexExcludes = appRuleDriver.getPrimedFlexExcludeDomains();
                        if (empty(flexExcludes)) {
                            log.debug("_primeApp: no flexExcludeDomains for device/app/rule/matcher: "
                                      + device.getName() + "/" + app.getName() + "/" + rule.getName() + "/"
                                      + matcher.getName());
                        } else {
                            flexExcludeDomains.addAll(flexExcludes);
                            areAllSetsEmpty = areAllSetsEmpty && empty(flexExcludes);
                        }
                    }
                }

                if (areAllSetsEmpty) continue;

                for (final String ip : accountDeviceIps.get(device.getUuid())) {
                    if (!empty(rejectDomains)) {
                        rejectDomains.removeAll(whiteListDomains);
                        AppRuleDriver.defineRedisRejectSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                           rejectDomains.toArray(String[]::new));
                    }
                    if (!empty(blockDomains)) {
                        blockDomains.removeAll(whiteListDomains);
                        AppRuleDriver.defineRedisBlockSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                          blockDomains.toArray(String[]::new));
                    }
                    if (!empty(whiteListDomains)) {
                        AppRuleDriver.defineRedisWhiteListSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                              whiteListDomains.toArray(String[]::new));
                    }
                    if (!empty(filterDomains)) {
                        AppRuleDriver.defineRedisFilterSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                           filterDomains.toArray(String[]::new));
                    }
                    if (!empty(requestHeaderModifiers)) {
                        AppRuleDriver.defineRedisResponseHeaderModifiersSet(
                                redis, ip, app.getName() + ":" + app.getUuid(),
                                requestHeaderModifiers.toArray(String[]::new));
                    }
                    if (account.isFirstAdmin()) {
                        if (!empty(flexDomains)) {
                            if (!empty(flexExcludeDomains)) flexDomains.removeAll(flexExcludeDomains);
                            AppRuleDriver.defineRedisFlexSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                             flexDomains.toArray(String[]::new));
                            updateFlexRouters = true;
                        }
                        if (!empty(flexExcludeDomains)) {
                            AppRuleDriver.defineRedisFlexExcludeSet(redis, ip, app.getName() + ":" + app.getUuid(),
                                                                    flexExcludeDomains.toArray(String[]::new));
                        }
                    }
                }
            }
        }

        if (updateFlexRouters) flexRouterService.updateFlexRoutes(flexDomains);
    }

}
