package bubble.test;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.storage.s3.S3StorageDriver;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class LiveNetworkTest extends NetworkTestBase {

    @Override protected Class<? extends CloudServiceDriver> getNetworkStorageDriver() { return S3StorageDriver.class; }

    //@Test public void testSimpleNetwork () throws Exception { modelTest("network/simple_network"); }

}
