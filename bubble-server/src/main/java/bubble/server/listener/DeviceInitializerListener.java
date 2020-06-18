/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.server.listener;

import bubble.dao.account.AccountDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import static bubble.model.account.Account.ROOT_USERNAME;

public class DeviceInitializerListener extends RestServerLifecycleListenerBase {

    @Override public void onStart(RestServer server) {
        final BubbleConfiguration configuration = (BubbleConfiguration) server.getConfiguration();
        final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
        final DeviceDAO deviceDAO = configuration.getBean(DeviceDAO.class);
        final BubbleNode thisNode = configuration.getThisNode();

        if (!configuration.isSageLauncher()) {
            for (Account a : accountDAO.findAll()) {
                if (!a.getEmail().equals(ROOT_USERNAME)) {
                    deviceDAO.ensureSpareDevice(a.getUuid(), thisNode.getNetwork(), false);
                }
            }
            deviceDAO.refreshVpnUsers();
        }
    }

}
