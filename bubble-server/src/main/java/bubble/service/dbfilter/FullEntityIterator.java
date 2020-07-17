/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.dbfilter;

import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.wizard.dao.AbstractCRUDDAO.ORDER_CTIME_ASC;

@Slf4j
public class FullEntityIterator extends EntityIterator {

    private final BubbleConfiguration config;
    private final BubbleNetwork network;

    public FullEntityIterator (BubbleConfiguration config,
                               BubbleNetwork network,
                               AtomicReference<Exception> error) {
        super(error);
        this.config = config;
        this.network = network;
    }

    protected void iterate() {
        config.getEntityClasses()
              .forEach(c -> addEntities(true, c, config.getDaoForEntityClass(c).findAll(ORDER_CTIME_ASC),
                                        network, null, null));
        log.info("iterate: completed");
    }

}
