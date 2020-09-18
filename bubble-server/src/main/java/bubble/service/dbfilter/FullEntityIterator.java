/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.dbfilter;

import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.LaunchType;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

import static bubble.model.device.Device.newUninitializedDevice;
import static org.cobbzilla.wizard.dao.AbstractCRUDDAO.ORDER_CTIME_ASC;

@Slf4j
public class FullEntityIterator extends EntityIterator {

    private final BubbleConfiguration config;
    private final Account account;
    private final BubbleNetwork network;
    private final LaunchType launchType;

    public FullEntityIterator (BubbleConfiguration config,
                               Account account,
                               BubbleNetwork network,
                               LaunchType launchType,
                               AtomicReference<Exception> error) {
        super(error);
        this.config = config;
        this.network = network;
        this.account = account;
        this.launchType = launchType;
    }

    protected void iterate() {
        config.getEntityClasses()
              .forEach(c -> addEntities(true, c, config.getDaoForEntityClass(c).findAll(ORDER_CTIME_ASC),
                                        network, null, null));
        if (account != null && launchType != null && launchType == LaunchType.fork_node) {
            // add an initial device so that algo starts properly the first time
            // name and totp key will be overwritten when the device is initialized for use
            log.info("iterate: creating a single dummy device for algo to start properly");
            final var initDevice = newUninitializedDevice(network.getUuid(), account.getUuid());
            add(config.getBean(DeviceDAO.class).create(initDevice));
        }
        log.info("iterate: completed");
    }

}
