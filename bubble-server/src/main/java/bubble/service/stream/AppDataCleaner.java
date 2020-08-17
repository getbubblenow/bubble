/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.dao.app.AppDataDAO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class AppDataCleaner extends SimpleDaemon {

    @Getter(lazy=true) private final long sleepTime = HOURS.toMillis(4);

    @Autowired private AppDataDAO dataDAO;

    @Override protected void process() {
        try {
            final int ct = dataDAO.bulkDeleteWhere("expiration < " + now());
            log.info("process: removed " + ct + " expired AppData records");
        } catch (Exception e) {
            reportError("AppDataCleaner.process: "+shortError(e), e);
        }
    }

}
