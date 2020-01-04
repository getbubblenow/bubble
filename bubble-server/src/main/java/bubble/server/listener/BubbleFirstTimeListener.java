package bubble.server.listener;

import bubble.ApiConstants;
import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SageHelloService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j
public class BubbleFirstTimeListener extends RestServerLifecycleListenerBase<BubbleConfiguration> {

    public static final File FIRST_TIME_FILE = new File(ApiConstants.HOME_DIR, "first_time_marker");
    public static final String UNLOCK_KEY = "__bubble_unlock_key__";
    public static final long UNLOCK_EXPIRATION = HOURS.toSeconds(24);
    public static final int UNLOCK_KEY_LEN = 6;

    private static AtomicReference<RedisService> redis = new AtomicReference<>();
    public static String getUnlockKey () {
        final RedisService r = redis.get();
        return r == null ? null : r.get(UNLOCK_KEY);
    }

    @Override public void onStart(RestServer server) {

        final BubbleConfiguration configuration = (BubbleConfiguration) server.getConfiguration();
        redis.set(configuration.getBean(RedisService.class));

        if (FIRST_TIME_FILE.exists()) {
            // final FirstTimeType firstTimeType = FirstTimeType.fromString(FileUtil.toStringOrDie(FIRST_TIME_FILE));
            final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
            final Account adminAccount = accountDAO.getFirstAdmin();
            if (adminAccount == null) {
                log.error("onStart: no admin account found, cannot send first time install message");
                return;
            }
            final BubbleNetwork network = configuration.getThisNetwork();

            final String unlockKey = randomAlphabetic(UNLOCK_KEY_LEN).toUpperCase();
            redis.get().set(UNLOCK_KEY, unlockKey, EX, UNLOCK_EXPIRATION);

            final SageHelloService helloService = configuration.getBean(SageHelloService.class);
            helloService.setUnlockMessage(new AccountMessage()
                    .setAccount(adminAccount.getUuid())
                    .setName(network.getUuid())
                    .setMessageType(AccountMessageType.request)
                    .setAction(AccountAction.verify)
                    .setTarget(ActionTarget.network)
                    .setData(unlockKey));

            if (!FIRST_TIME_FILE.delete()) {
                log.error("onStart: error deleting: "+abs(FIRST_TIME_FILE));
            }
        }
    }

}
