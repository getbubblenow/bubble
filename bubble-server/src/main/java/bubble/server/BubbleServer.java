/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.server;

import bubble.model.cloud.BubbleNode;
import bubble.server.listener.BubbleFirstTimeListener;
import bubble.server.listener.NodeInitializerListener;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerBase;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizard.server.listener.BrowserLauncherListener;
import org.cobbzilla.wizard.server.listener.ErrbitConfigListener;
import org.cobbzilla.wizard.server.listener.FlywayMigrationListener;
import org.cobbzilla.wizard.server.listener.SystemInitializerListener;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.cloud.BubbleNode.nodeFromFile;
import static bubble.server.BubbleConfiguration.jarVersion;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_ALL_ADDRS;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_LOCALHOST;
import static org.cobbzilla.util.system.CommandShell.loadShellExports;
import static org.cobbzilla.util.system.OutOfMemoryErrorUncaughtExceptionHandler.EXIT_ON_OOME;

@NoArgsConstructor @Slf4j
public class BubbleServer extends RestServerBase<BubbleConfiguration> {

    public static final String API_CONFIG_YML = "bubble-config.yml";
    public static final String BUBBLE_DUMP_CONFIG = "BUBBLE_DUMP_CONFIG";

    public static final List<RestServerLifecycleListener> LIFECYCLE_LISTENERS = Arrays.asList(new RestServerLifecycleListener[] {
            new ErrbitConfigListener(),
            new SystemInitializerListener(),
            new FlywayMigrationListener<BubbleConfiguration>(),
            new NodeInitializerListener(),
            new BubbleFirstTimeListener(),
            new BrowserLauncherListener()
    });

    public static final List<RestServerLifecycleListener> RESTORE_LIFECYCLE_LISTENERS = Arrays.asList(new RestServerLifecycleListener[] {
            new NodeInitializerListener(),
            new BubbleFirstTimeListener()
    });

    public static final String[] DEFAULT_ENV_FILE_PATHS = {
            HOME_DIR + ".bubble.env",
            HOME_DIR + "/api/bubble.env",
            System.getProperty("user.dir") + "/bubble.env"
    };

    private static final AtomicReference<String> restoreKey = new AtomicReference<>();
    public static boolean isRestoreMode () { return restoreKey.get() != null; }
    public static void disableRestoreMode () {
        final BubbleNode selfNode = nodeFromFile(THIS_NODE_FILE);
        FileUtil.toFileOrDie(THIS_NODE_FILE, json(selfNode.setRestoreKey(null).setWasRestored(null)));
        restoreKey.set(null);
    }

    @Override protected String getListenAddress() { return listenAll() ? IPv4_ALL_ADDRS : IPv4_LOCALHOST; }

    private boolean listenAll() { return Boolean.parseBoolean(System.getProperty("bubble.listenAll")); }

    // config is loaded from the classpath
    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        Thread.setDefaultUncaughtExceptionHandler(EXIT_ON_OOME);

        final Map<String, String> env = loadEnvironment(args);
        final ConfigurationSource configSource = getConfigurationSource();

        restoreKey.set(getRestoreKey());
        if (restoreKey.get() != null) {
            log.info("Starting BubbleServer ("+ jarVersion()+") in restore mode...");
            main(BubbleServer.class, RESTORE_LIFECYCLE_LISTENERS, configSource, env);
        } else {
            if (Boolean.parseBoolean(env.get(BUBBLE_DUMP_CONFIG))) {
                log.info("Dumping BubbleConfiguration...");
                dumpConfig(configSource);
            } else {
                log.info("Starting BubbleServer ("+ jarVersion()+")...");
                main(BubbleServer.class, LIFECYCLE_LISTENERS, configSource, env);
            }
        }
    }

    public static void dumpConfig(ConfigurationSource configSource) throws Exception {
        main(BubbleServer.class, new RestServerLifecycleListenerBase() {
            @Override public void beforeStart(RestServer server) {
                System.out.println(json(server.getConfiguration()));
                System.exit(1);
            }
        }, configSource);
    }

    public static String getRestoreKey() {
        if (!THIS_NODE_FILE.exists()) return null;
        final BubbleNode selfNode = nodeFromFile(THIS_NODE_FILE);
        return empty(selfNode.getRestoreKey()) ? null : selfNode.getRestoreKey();
    }

    public static Map<String, String> loadEnvironment() { return loadEnvironment(null); }

    public static Map<String, String> loadEnvironment(String[] args) {
        final Map<String, String> env = new HashMap<>(System.getenv());
        try {
            final File envFile = getEnvFile(args);
            if (envFile == null) return die("loadEnvironment: no env file found");
            if (!envFile.exists()) return die("loadEnvironment: env file does not exist: "+abs(envFile));

            log.info("loadEnvironment: loading env from: "+abs(envFile));
            env.putAll(loadShellExports(envFile));
            log.debug("loadEnvironment: loaded env="+json(env));

        } catch (Exception e) {
            log.warn("Error loading environment: "+e);
        }
        return env;
    }

    public static File getEnvFile(String[] args) {
        return args != null && args.length == 1 ? new File(args[0]) : getDefaultEnvFile();
    }

    public static File getDefaultEnvFile() {
        return Arrays.stream(DEFAULT_ENV_FILE_PATHS)
                .map(File::new)
                .filter(File::exists)
                .findFirst()
                .orElse(null);
    }

    public static ConfigurationSource getConfigurationSource() {
        return getStreamConfigurationSource(BubbleServer.class, API_CONFIG_YML);
    }

}
