/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.app;

import bubble.model.app.AppSite;
import bubble.service.device.DeviceService;
import bubble.service.stream.RuleEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AppSiteDAO extends AppTemplateEntityDAO<AppSite> {

    @Autowired private RuleEngineService ruleEngineService;
    @Autowired private DeviceService deviceService;

    @Override public AppSite postCreate(AppSite site, Object context) {
        // todo: update entities based on this template if account has updates enabled
        if (site.hasMaxSecurityHosts()) deviceService.initDeviceSecurityLevels();
        return super.postCreate(site, context);
    }

    @Override public AppSite postUpdate(AppSite site, Object context) {
        ruleEngineService.flushCaches();
        if (site.hasMaxSecurityHosts()) deviceService.initDeviceSecurityLevels();
        return super.postUpdate(site, context);
    }

    @Override public void delete(String uuid) {
        final AppSite site = findByUuid(uuid);
        if (site != null) {
            getConfiguration().deleteDependencies(site);
            super.delete(uuid);
            ruleEngineService.flushCaches();
        }
    }
}
