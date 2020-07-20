/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.upgrade;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import bubble.service.upgrade.AppObjectUpgradeHandler;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static bubble.model.cloud.notify.NotificationType.upgrade_apps_response;
import static bubble.service.upgrade.AppObjectUpgradeHandler.APP_UPGRADE_HANDLERS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_upgrade_apps_request extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private BubbleAppDAO appDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) {
            die("sender not found: "+n.getFromNode());
        } else {
            final AppsUpgradeNotification request = json(n.getPayloadJson(), AppsUpgradeNotification.class);
            final AppsUpgradeNotification response = new AppsUpgradeNotification();
            final Account admin = accountDAO.getFirstAdmin();
            if (request.hasDrivers()) {
                for (RuleDriver driver : request.getDrivers()) {
                    response.addDriver(driverDAO.findPublicTemplateByName(admin.getUuid(), driver.getName()));
                }
            }
            if (request.hasApps()) {
                for (BubbleApp app : request.getApps()) {
                    final BubbleApp sageApp = appDAO.findByAccountAndName(admin.getUuid(), app.getName());
                    if (sageApp == null) {
                        log.warn("handleNotification: requested app not found: "+app.getName());
                    } else {
                        response.addApp(appDAO.findPublicTemplateByName(admin.getUuid(), app.getName()));
                        for (AppObjectUpgradeHandler handler : APP_UPGRADE_HANDLERS) {
                            final List sageObjects = handler.findSageObjects(configuration, admin, sageApp);
                            log.info("handleNotification: findSageObjects(app=" + app.getName() + ", " + handler.getAppObjectClass().getSimpleName() + ") found: " + StringUtil.toString(sageObjects));
                            response.addAppObjects(sageObjects);
                        }
                    }
                }
            }
            log.info("handleNotification: returning response: "+response);
            notifySender(upgrade_apps_response, n.getNotificationId(), sender, response);
        }
    }

}
