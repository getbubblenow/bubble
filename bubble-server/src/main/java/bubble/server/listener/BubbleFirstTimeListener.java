/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.server.listener;

import bubble.ApiConstants;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SageHelloService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.toStringOrDie;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j
public class BubbleFirstTimeListener extends RestServerLifecycleListenerBase<BubbleConfiguration> {

    public static final File FIRST_TIME_FILE = new File(ApiConstants.HOME_DIR, "first_time_marker");
    public static final String UNLOCK_KEY = "__bubble_unlock_key__";
    public static final long UNLOCK_EXPIRATION = HOURS.toSeconds(48);
    public static final int UNLOCK_KEY_LEN = 6;

    private static final FirstTimeType FIRST_TIME_TYPE_DEFAULT = FirstTimeType.install;

    private static AtomicReference<RedisService> redis = new AtomicReference<>();
    public static String getUnlockKey () {
        final RedisService r = redis.get();
        return r == null ? null : r.get(UNLOCK_KEY);
    }

    @Override public void onStart(RestServer server) {

        final BubbleConfiguration configuration = (BubbleConfiguration) server.getConfiguration();
        redis.set(configuration.getBean(RedisService.class));

        final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
        final var network = configuration.getThisNetwork();
        if (FIRST_TIME_FILE.exists()) {
            try {
                try {
                    final var firstTimeType = FirstTimeType.fromString(toStringOrDie(FIRST_TIME_FILE));
                    updateNetworkState(configuration, network, firstTimeType);
                } catch (Exception e) {
                    log.warn("Cannot open and/or read/parse first time file " + FIRST_TIME_FILE.getAbsolutePath());
                    updateNetworkState(configuration, network, FIRST_TIME_TYPE_DEFAULT);
                }

                final Account adminAccount = accountDAO.getFirstAdmin();
                if (adminAccount == null) {
                    log.error("onStart: no admin account found, cannot send first time install message, unlocking now");
                    accountDAO.unlock();
                    return;
                }

                final AccountPolicy adminPolicy = configuration.getBean(AccountPolicyDAO.class).findSingleByAccount(adminAccount.getUuid());
                if (adminPolicy == null || !adminPolicy.hasVerifiedNonAuthenticatorAccountContacts()) {
                    log.error("onStart: no AccountPolicy found (or no verified non-authenticator contacts) for admin account (" + adminAccount.getEmail() + "), cannot send first time install message, unlocking now");
                    accountDAO.unlock();
                    return;
                }

                final AccountMessage readyMessage = new AccountMessage()
                        .setAccount(adminAccount.getUuid())
                        .setNetwork(network.getUuid())
                        .setName(network.getUuid())
                        .setMessageType(AccountMessageType.request)
                        .setAction(AccountAction.verify)
                        .setTarget(ActionTarget.network);
                if (!network.launchLock()) {
                    log.info("onStart: thisNetwork.launchLock was false, unlocking now");
                    accountDAO.unlock();
                    readyMessage.setData(null);
                } else {
                    final String unlockKey = randomAlphabetic(UNLOCK_KEY_LEN).toUpperCase();
                    redis.get().set(UNLOCK_KEY, unlockKey, EX, UNLOCK_EXPIRATION);
                    readyMessage.setData(unlockKey);
                }
                configuration.getBean(SageHelloService.class).setUnlockMessage(readyMessage);

            } finally {
                if (!FIRST_TIME_FILE.delete()) {
                    log.error("onStart: error deleting: "+abs(FIRST_TIME_FILE));
                }
            }
        } else {
            if (!accountDAO.locked()) {
                log.info("onStart: system is not locked, ensuring all accounts are unlocked");
                accountDAO.unlock();
                updateNetworkState(configuration, network, FIRST_TIME_TYPE_DEFAULT);
            }
        }
    }

    private void updateNetworkState(@NonNull final BubbleConfiguration config, @NonNull final BubbleNetwork network,
                                    @NonNull final FirstTimeType firstTimeType) {
        if (network.getState() == BubbleNetworkState.starting) {
            network.setState(firstTimeType == FirstTimeType.restore ? BubbleNetworkState.restoring
                                                                    : BubbleNetworkState.running);
            config.getBean(BubbleNetworkDAO.class).update(network);
        }
    }

}
