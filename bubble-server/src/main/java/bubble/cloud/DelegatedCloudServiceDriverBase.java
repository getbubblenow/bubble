/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.notify.NotificationService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.CloudCredentials.PARAM_DELEGATE_NODE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public abstract class DelegatedCloudServiceDriverBase extends CloudServiceDriverBase<CloudCredentials> {

    protected CloudService cloud;

    @Autowired @Getter protected BubbleConfiguration configuration;
    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected NotificationService notificationService;

    public DelegatedCloudServiceDriverBase (CloudService cloud) {
        this.cloud = cloud;
        setCredentials(cloud.getCredentials());
    }

    public BubbleNode getDelegateNode() {
        final BubbleNode delegate = nodeDAO.findByUuid(credentials.getParam(PARAM_DELEGATE_NODE));
        return delegate != null ? delegate : die(getClass().getSimpleName()+".getDelegateNode: delegate not found: "+credentials.getParam(PARAM_DELEGATE_NODE));
    }

}
