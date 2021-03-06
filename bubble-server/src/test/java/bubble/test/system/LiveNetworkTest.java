/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.storage.s3.S3StorageDriver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LiveNetworkTest extends NetworkTestBase {

    @Override protected Class<? extends CloudServiceDriver> getNetworkStorageDriver() { return S3StorageDriver.class; }

    //@Test public void testSimpleNetwork () throws Exception { modelTest("network/simple_network"); }

}
