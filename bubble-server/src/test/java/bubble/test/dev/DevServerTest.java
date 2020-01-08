package bubble.test.dev;

import bubble.resources.EntityConfigsResource;
import bubble.server.BubbleConfiguration;
import bubble.test.ActivatedBubbleModelTestBase;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class DevServerTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-dev"; }
    @Override protected boolean useMocks() { return false; }
    @Override protected String getDatabaseNameSuffix() { return "dev"; }
    @Override protected boolean dropPreExistingDatabase() { return false; }
    @Override protected boolean allowPreExistingDatabase() { return true; }
    @Override public boolean doTruncateDb() { return false; }
    @Override protected boolean createSqlIndexes () { return true; }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        getConfiguration().getBean(EntityConfigsResource.class).getAllowPublic().set(true);
        super.onStart(server);
    }

    @Test public void runDevServer () throws Exception {
        log.info("runDevServer: Bubble API server started and model initialized. You may now begin testing.");
        sleep(DAYS.toMillis(30), "running dev server");
    }

}
