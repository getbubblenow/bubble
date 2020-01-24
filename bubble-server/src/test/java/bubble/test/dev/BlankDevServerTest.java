package bubble.test.dev;

import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.listener.FlywayMigrationListener;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class BlankDevServerTest extends NewBlankDevServerTest {

    @Override protected String getDatabaseNameSuffix() { return "test"; }
    @Override protected boolean dropPreExistingDatabase() { return false; }
    @Override protected boolean allowPreExistingDatabase() { return true; }
    @Override public boolean doTruncateDb() { return false; }
    @Override public boolean shouldFlushRedis() { return false; }

    @Override protected Collection<RestServerLifecycleListener> getLifecycleListeners() {
        final Collection<RestServerLifecycleListener> listeners = new ArrayList<>(TEST_LIFECYCLE_LISTENERS);
        listeners.add(new FlywayMigrationListener<BubbleConfiguration>());
        getConfiguration().getDatabase().setMigrationEnabled(true);
        return listeners;
    }

    @Test public void runBlankServer () throws Exception {
        log.info("runBlankServer: Bubble API server started and model initialized. You may now begin testing.");
        sleep(DAYS.toMillis(30), "running dev server");
    }

}
