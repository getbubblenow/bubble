package bubble.test;

import bubble.cloud.email.mock.MockEmailDriver;
import bubble.cloud.sms.mock.MockSmsDriver;
import bubble.server.BubbleConfiguration;
import bubble.server.BubbleServer;
import bubble.server.listener.NodeInitializerListener;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.cobbzilla.wizard.client.script.ApiScriptIncludeHandler;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizardtest.resources.ApiModelTestBase;

import java.io.File;
import java.util.*;

import static bubble.ApiConstants.ENTITY_CONFIGS_ENDPOINT;
import static bubble.test.BubbleTestBase.ENV_EXPORT_FILE;
import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.system.CommandShell.loadShellExportsOrDie;

@Slf4j
public abstract class BubbleModelTestBase extends ApiModelTestBase<BubbleConfiguration, BubbleServer> {

    public static final List<RestServerLifecycleListener> TEST_LIFECYCLE_LISTENERS = asList(new RestServerLifecycleListener[] {
            new NodeInitializerListener()
    });

    // disable model cache for all tests
    @Override public File permCacheDir() { return null; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        server.getConfiguration().setBackupsEnabled(backupsEnabled());
        super.beforeStart(server);
    }

    public boolean backupsEnabled() { return false; }

    @Getter(lazy=true) private final ApiClientBase _api = new TestBubbleApiClient(getConfiguration());
    @Override public ApiClientBase getApi() { return get_api(); }

    private BubbleApiRunnerListener getListener() { return new BubbleApiRunnerListener(getConfiguration()); }

    @Override protected Collection<RestServerLifecycleListener> getLifecycleListeners() { return TEST_LIFECYCLE_LISTENERS; }

    @Getter(lazy=true) private final ApiRunner apiRunner = new ApiRunner(getApi(), (ApiRunnerListener) getListener()) {
        @Override public ApiScriptIncludeHandler getIncludeHandler() {
            return path -> {
                for (String prefix : getIncludePaths()) {
                    if (!prefix.endsWith("/") && !path.startsWith("/")) prefix = prefix + "/";
                    final String data = FileUtil.toStringOrDie(prefix + path + ".json");
                    if (!empty(data)) return data;
                }
                return die("include("+path+"): not found among: "+getIncludePaths());
            };
        }

        @Override protected Handlebars initHandlebars() {
            ctx.putAll((Map) getConfiguration().getEnvironment());
            ctx.put("configuration", getConfiguration());
            final Handlebars hb = super.initHandlebars();
            return HandlebarsTestHelpers.registerHelpers(hb);
        }
    };

    @Override protected String getEntityConfigsEndpoint() { return ENTITY_CONFIGS_ENDPOINT; }

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

    @Override protected List<String> getIncludePaths() {
        final List<String> include = new ArrayList<>();
        include.add("models/include");
        include.add("src/test/resources/models/include");
        include.addAll(super.getIncludePaths());
        return include;
    }

}
