package bubble.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        AuthTest.class,
        PaymentTest.class,
        S3StorageTest.class
})
public class BubbleCoreSuite {}
