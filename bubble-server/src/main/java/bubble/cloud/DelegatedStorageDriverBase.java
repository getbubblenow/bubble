/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud;

import bubble.cloud.storage.StorageServiceDriverBase;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.service.notify.NotificationService;
import bubble.service.cloud.StorageStreamService;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.CloudCredentials.PARAM_DELEGATE_NODE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public abstract class DelegatedStorageDriverBase extends StorageServiceDriverBase<CloudCredentials> {

    protected CloudService cloud;

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected NotificationService notificationService;
    @Autowired protected StorageStreamService storageStreamService;

    public DelegatedStorageDriverBase (CloudService cloud) {
        this.cloud = cloud;
        setCredentials(cloud.getCredentials());
    }

    public BubbleNode getDelegateNode() {
        final BubbleNode delegate = nodeDAO.findByUuid(credentials.getParam(PARAM_DELEGATE_NODE));
        return delegate != null ? delegate : die(getClass().getSimpleName()+".getDelegateNode: delegate not found: "+credentials.getParam(PARAM_DELEGATE_NODE));
    }

}
