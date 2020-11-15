/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test;

import bubble.cloud.email.mock.MockEmailDriver;
import bubble.cloud.sms.mock.MockSmsDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.server.BubbleServer;
import bubble.server.listener.NodeInitializerListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizard.server.listener.ErrbitConfigListener;
import org.cobbzilla.wizardtest.resources.ApiModelTestBase;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static bubble.service.boot.StandardSelfNodeService.SELF_NODE_JSON;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static bubble.test.BubbleTestBase.ENV_EXPORT_FILE;
import static bubble.test.HandlebarsTestHelpers.registerTestHelpers;
import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.system.CommandShell.loadShellExportsOrDie;

@Slf4j
public abstract class BubbleModelTestBase extends ApiModelTestBase<BubbleConfiguration, BubbleServer> {

    public static final List<RestServerLifecycleListener> TEST_LIFECYCLE_LISTENERS = asList(new RestServerLifecycleListener[] {
            new ErrbitConfigListener(),
            new NodeInitializerListener()
    });

    protected boolean hasExistingDb = false;
    protected static BubbleServer server = null;

    // disable model cache for all tests
    @Override public File permCacheDir() { return null; }

    @Before public void resetBubbleServer() {
        server = (BubbleServer) getServer();
        try {
            final BubbleNode thisNode = server.getConfiguration().getThisNode();
            log.info("resetBubbleServer: set server (thisNode=" + (thisNode == null ? null : thisNode.id()) + ")");
        } catch (Exception e) {
            log.warn("resetBubbleServer: "+shortError(e));
        }
        final BubbleConfiguration configuration = getConfiguration();
        final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
        final AccountPolicyDAO policyDAO = configuration.getBean(AccountPolicyDAO.class);
        CloudService.flushDriverCache();

        if (shouldFlushRedis()) configuration.getBean(RedisService.class).flush();
        final Account root = accountDAO.getFirstAdmin();
        if (root != null) {
            final AccountPolicy rootPolicy = policyDAO.findSingleByAccount(root.getUuid());
            policyDAO.delete(rootPolicy.getUuid());
            policyDAO.create(new AccountPolicy().setAccount(root.getUuid()));
        }
    }

    protected boolean shouldFlushRedis() { return true; }

    @AfterClass public static void resetSelfJson () {
        if (server != null) server.stopServer();
        final File selfJson = new File(SELF_NODE_JSON);
        if (selfJson.exists()) {
            if (!selfJson.delete()) die("resetSelfJson: error deleting "+abs(SELF_NODE_JSON));
        }
    }

    public boolean backupsEnabled() { return false; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();
        configuration.setBackupsEnabled(backupsEnabled());
        if (configuration.dbExists()) {
            hasExistingDb = true;
            log.info("beforeStart: not deleting "+abs(THIS_NODE_FILE)+" because DB exists");
        } else {
            // start fresh
            if (THIS_NODE_FILE.exists() && !THIS_NODE_FILE.delete()) {
                die("beforeStart: error deleting " + abs(THIS_NODE_FILE));
            }
        }

        // this will start a redis server
        super.beforeStart(server);

        // now we can set redis port in config
        configuration.getRedis().setPort(getRedisPort());
    }

    @Getter(lazy=true) private final ApiClientBase _api = new TestBubbleApiClient(getConfiguration());
    @Override public ApiClientBase getApi() { return get_api(); }

    private BubbleApiRunnerListener getListener() { return new BubbleApiRunnerListener(getConfiguration()); }

    @Override protected Collection<RestServerLifecycleListener> getLifecycleListeners() { return TEST_LIFECYCLE_LISTENERS; }

    @Getter(lazy=true) private final ApiRunner apiRunner = initApiRunner();
    private ApiRunner initApiRunner() {
        final ApiRunner runner = new ApiRunner(getApi(), (ApiRunnerListener) getListener());
        registerTestHelpers(runner.getHandlebars());
        return runner;
    }

    @Getter private StreamConfigurationSource configurationSource
            = new StreamConfigurationSource("test-bubble-config.yml");

    @Override protected Map<String, String> getServerEnvironment() {
        final Map<String, String> env = loadShellExportsOrDie(ENV_EXPORT_FILE);
        if (!env.containsKey("BUBBLE_HBM2DDL_AUTO")) {
            env.put("BUBBLE_HBM2DDL_AUTO", "create");
        }
        if (useMocks()) {
            env.put("BUBBLE_SMTP_DRIVER", MockEmailDriver.class.getName());
            env.put("BUBBLE_SMS_DRIVER", MockSmsDriver.class.getName());
        }
        return env;
    }

    protected boolean useMocks() { return true; }

    @Override public List<String> getIncludePaths() {
        final List<String> include = new ArrayList<>();
        include.add("models/include");
        include.add("src/test/resources/models/include");
        include.addAll(super.getIncludePaths());
        return include;
    }

}
