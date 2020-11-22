/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email.mailgun;

import bubble.cloud.email.EmailApiDriverConfig;
import lombok.Getter;
import lombok.Setter;

public class MailgunEmailDriverConfig extends EmailApiDriverConfig {

    @Getter @Setter private String domain;

}
