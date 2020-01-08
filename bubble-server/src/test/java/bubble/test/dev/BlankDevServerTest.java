package bubble.test.dev;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class BlankDevServerTest extends NewBlankDevServerTest {

    @Override protected String getDatabaseNameSuffix() { return "test"; }
    @Override protected boolean dropPreExistingDatabase() { return false; }
    @Override protected boolean allowPreExistingDatabase() { return true; }
    @Override public boolean doTruncateDb() { return false; }

    @Test public void runBlankServer () throws Exception {
        log.info("runBlankServer: Bubble API server started and model initialized. You may now begin testing.");
        sleep(DAYS.toMillis(30), "running dev server");
    }

}
