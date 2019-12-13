package bubble.test;

import bubble.cloud.email.mock.MockEmailDriver;
import bubble.cloud.sms.mock.MockSmsDriver;
import bubble.server.BubbleConfiguration;
import bubble.server.BubbleServer;
import lombok.Getter;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizardtest.resources.AbstractResourceIT;

import java.util.Map;

import static org.cobbzilla.util.system.CommandShell.loadShellExportsOrDie;

public abstract class BubbleTestBase extends AbstractResourceIT<BubbleConfiguration, BubbleServer> {

    public static final String ENV_EXPORT_FILE = ".bubble-test.env";

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

    @Getter(lazy=true) private final ApiClientBase _api = new TestBubbleApiClient(getConfiguration());
    @Override public ApiClientBase getApi() { return get_api(); }

}
