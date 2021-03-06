/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import bubble.model.account.AccountTemplate;
import bubble.server.BubbleConfiguration;

public interface AppTemplateEntity extends AccountTemplate {

    String getApp();
    <T extends AppTemplateEntity> T setApp(String app);

    default <T extends AppTemplateEntity> void upgrade(T sageObject, BubbleConfiguration configuration) {
        update(sageObject);
    }

}
