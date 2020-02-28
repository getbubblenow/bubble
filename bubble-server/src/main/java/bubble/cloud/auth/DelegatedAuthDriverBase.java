/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.auth;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.NotificationType;
import bubble.notify.auth.AuthDriverNotification;
import bubble.notify.email.EmailDriverNotification;
import com.fasterxml.jackson.databind.JsonNode;

import static org.cobbzilla.util.json.JsonUtil.json;

public abstract class DelegatedAuthDriverBase extends DelegatedCloudServiceDriverBase implements AuthenticationDriver {

    public DelegatedAuthDriverBase(CloudService cloud) { super(cloud); }

    protected AuthDriverNotification notification(AuthDriverNotification n) { return n.setAuthService(cloud.getDelegated()); }

    protected abstract NotificationType getSendNotificationType();

    protected abstract Class<? extends RenderedMessage> getRenderedMessageClass();

    protected abstract String getDefaultTemplatePath();

    @Override public String getTemplatePath() {
        final JsonNode driverConfig = cloud.getDriverConfig();
        return driverConfig != null && driverConfig.has("templatePath")
                ? driverConfig.get("templatePath").textValue()
                : getDefaultTemplatePath();
    }

    @Override public boolean send(RenderedMessage message) {
        final BubbleNode delegate = getDelegateNode();
        if (!configuration.testMode()) message.getCtx().clear();
        return notificationService.notifySync(delegate, getSendNotificationType(), notification(new EmailDriverNotification()
                .setRenderedMessage(json(json(message), JsonNode.class))
        .setRenderedMessageClass(getRenderedMessageClass().getName())));
    }

}
