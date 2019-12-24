package bubble.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        DbInit.class,
        AuthTest.class,
        PaymentTest.class,
        S3StorageTest.class,
        DriverTest.class,
        ProxyTest.class,
        TrafficAnalyticsTest.class,
        NetworkTest.class
})
public class BubbleCoreSuite {}
