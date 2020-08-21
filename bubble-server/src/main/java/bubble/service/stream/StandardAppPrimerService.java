/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.device.Device;
import bubble.rule.AppRuleDriver;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.DeviceIdService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;

@Service @Slf4j
public class StandardAppPrimerService implements AppPrimerService {

    @Autowired private AccountDAO accountDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceIdService deviceIdService;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleDriverDAO driverDAO;
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
        if (thisNetwork.getInstallType() != AnsibleInstallType.node) {
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
    }

    public void prime(Account account) {
        deviceIdService.initBlockStats(account);
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

    @Getter(lazy=true) private final ExecutorService primerThread = fixedPool(1);

    private void prime(Account account, BubbleApp singleApp) {
        if (!isPrimingEnabled()) {
            log.info("prime: not enabled");
            return;
        }
        getPrimerThread().submit(() -> _prime(account, singleApp));
    }

    private synchronized void _prime(Account account, BubbleApp singleApp) {
        try {
            final Map<String, List<String>> accountDeviceIps = new HashMap<>();
            final List<Device> devices = deviceDAO.findByAccount(account.getUuid());
            for (Device device : devices) {
                accountDeviceIps.put(device.getUuid(), deviceIdService.findIpsByDevice(device.getUuid()));
            }
            if (accountDeviceIps.isEmpty()) return;

            final List<BubbleApp> appsToPrime = singleApp == null
                    ? appDAO.findByAccount(account.getUuid()).stream()
                    .filter(BubbleApp::canPrime)
                    .collect(Collectors.toList())
                    : new SingletonList<>(singleApp);
            for (BubbleApp app : appsToPrime) {
                final List<AppRule> rules = ruleDAO.findByAccountAndApp(account.getUuid(), app.getUuid());
                final List<AppMatcher> matchers = matcherDAO.findByAccountAndApp(account.getUuid(), app.getUuid());
                for (AppRule rule : rules) {
                    final RuleDriver driver = driverDAO.findByUuid(rule.getDriver());
                    if (driver == null) {
                        log.warn("_prime: driver not found for app/rule " + app.getName() + "/" + rule.getName() + ": " + rule.getDriver());
                        continue;
                    }
                    for (Device device : devices) {
                        final Set<String> rejectDomains = new HashSet<>();
                        final Set<String> blockDomains = new HashSet<>();
                        final Set<String> filterDomains = new HashSet<>();
                        for (AppMatcher matcher : matchers) {
                            final AppRuleDriver appRuleDriver = rule.initDriver(app, driver, matcher, account, device);
                            final Set<String> rejects = appRuleDriver.getPrimedRejectDomains();
                            if (empty(rejects)) {
                                log.debug("_prime: no rejectDomains for device/app/rule/matcher: " + device.getName() + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                            } else {
                                rejectDomains.addAll(rejects);
                            }
                            final Set<String> blocks = appRuleDriver.getPrimedBlockDomains();
                            if (empty(blocks)) {
                                log.debug("_prime: no blockDomains for device/app/rule/matcher: " + device.getName() + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                            } else {
                                blockDomains.addAll(blocks);
                            }
                            final Set<String> filters = appRuleDriver.getPrimedFilterDomains();
                            if (empty(filters)) {
                                log.debug("_prime: no filterDomains for device/app/rule/matcher: " + device.getName() + "/" + app.getName() + "/" + rule.getName() + "/" + matcher.getName());
                            } else {
                                filterDomains.addAll(filters);
                            }
                        }
                        if (!empty(rejectDomains) || !empty(blockDomains) || !empty(filterDomains)) {
                            for (String ip : accountDeviceIps.get(device.getUuid())) {
                                if (!empty(rejectDomains)) {
                                    AppRuleDriver.defineRedisRejectSet(redis, ip, app.getName() + ":" + app.getUuid(), rejectDomains.toArray(String[]::new));
                                }
                                if (!empty(blockDomains)) {
                                    AppRuleDriver.defineRedisBlockSet(redis, ip, app.getName() + ":" + app.getUuid(), blockDomains.toArray(String[]::new));
                                }
                                if (!empty(filterDomains)) {
                                    AppRuleDriver.defineRedisFilterSet(redis, ip, app.getName() + ":" + app.getUuid(), filterDomains.toArray(String[]::new));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            die("_prime: "+shortError(e), e);
        }
    }

}
