/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;

import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.daemon.ZillaRuntime.lazyGet;

@Slf4j
public class HttpStreamDebug {

    // for some reason @Getter(lazy=true) causes compilation problems when other classes try to call getter
    private static final AtomicReference<String> logFqdn = new AtomicReference<>();
    public static String getLogFqdn() {
        return lazyGet(logFqdn,
                () -> FileUtil.toStringOrDie(HOME_DIR + "/log_fqdn").trim(),
                () -> "~log_fqdn_disabled");
    }

}
