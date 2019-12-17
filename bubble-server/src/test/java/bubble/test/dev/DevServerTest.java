package bubble.test.dev;

import bubble.test.ActivatedBubbleModelTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class DevServerTest extends ActivatedBubbleModelTestBase {

    @Override protected String getModelPrefix() { return "models/"; }
    @Override protected String getManifest() { return "manifest-dev"; }

    @Override protected boolean useMocks() { return false; }

    @Override protected String getDatabaseNameSuffix() { return "dev"; }
    @Override protected boolean dropPreExistingDatabase() { return false; }
    @Override protected boolean allowPreExistingDatabase() { return true; }

    @Test public void runDevServer () throws Exception {
        log.info("runDevServer: Bubble API server started and model initialized. You may now begin testing.");
        sleep(DAYS.toMillis(30), "running dev server");
    }

}
