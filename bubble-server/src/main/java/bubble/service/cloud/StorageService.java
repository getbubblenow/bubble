/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.cloud;

import java.io.IOException;

public interface StorageService {

    boolean exists(String account, String tgzB64);

    void delete(String account, String path) throws IOException;
}
