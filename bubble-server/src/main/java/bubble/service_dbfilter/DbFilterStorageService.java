/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.service.cloud.StorageService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterStorageService implements StorageService {

    @Override public boolean exists(String account, String tgzB64) { return notSupported("exists"); }

    @Override public void delete(String account, String path) { notSupported("delete"); }

}
