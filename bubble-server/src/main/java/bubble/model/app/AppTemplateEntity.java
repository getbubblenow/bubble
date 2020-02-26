/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app;

import bubble.model.account.AccountTemplate;

public interface AppTemplateEntity extends AccountTemplate {

    String getApp();

}
