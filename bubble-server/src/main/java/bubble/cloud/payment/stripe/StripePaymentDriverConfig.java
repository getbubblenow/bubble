/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.payment.stripe;

import lombok.Getter;
import lombok.Setter;

public class StripePaymentDriverConfig {

    @Getter @Setter private String publicApiKey;

}
