package bubble.test.dev;

import bubble.resources.EntityConfigsResource;
import bubble.server.BubbleConfiguration;
import bubble.test.BubbleModelTestBase;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class NewBlankDevServerTest extends BubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-empty"; }
    @Override protected boolean useMocks() { return false; }
    @Override protected boolean createSqlIndexes () { return true; }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        getConfiguration().getBean(EntityConfigsResource.class).getAllowPublic().set(true);
        super.onStart(server);
    }

    @Test public void runBlankServer () throws Exception {
        log.info("runBlankServer: Bubble API server started and model initialized. You may now begin testing.");
        sleep(DAYS.toMillis(30), "running dev server");
    }

}
