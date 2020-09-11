/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @Accessors(chain=true)
public class FlexRouterPing {

    public static final long MAX_PING_AGE = SECONDS.toMillis(30);
    public static final long MIN_PING_AGE = -1 * SECONDS.toMillis(5);

    @Getter @Setter private long time;
    @Getter @Setter private String salt;
    @Getter @Setter private String hash;

    public FlexRouterPing (FlexRouter router) {
        time = now();
        salt = randomAlphanumeric(50);
        hash = sha256_hex(data(router));
    }

    public boolean validate(FlexRouter router) {
        if (empty(salt) || salt.length() < 50) return false;
        final long age = now() - time;
        if (age > MAX_PING_AGE || age < MIN_PING_AGE) return false;
        return sha256_hex(data(router)).equals(hash);
    }

    private String data(FlexRouter router) { return salt + ":" + time + ":" + router.getToken(); }

}
