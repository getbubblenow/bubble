/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.sms;

import lombok.Getter;
import lombok.Setter;

import static bubble.ApiConstants.MESSAGE_RESOURCE_BASE;

public class SmsConfig {

    public static final String DEFAULT_TEMPLATE_PATH = MESSAGE_RESOURCE_BASE+"[[locale]]/sms";

    @Getter @Setter private String templatePath = DEFAULT_TEMPLATE_PATH;

}
