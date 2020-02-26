/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.payment;

import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentNotification extends SynchronousNotification {

    @Getter @Setter private String cloud;
    @Getter @Setter private String planUuid;
    @Getter @Setter private String accountPlanUuid;
    @Getter @Setter private String paymentMethodUuid;
    @Getter @Setter private String billUuid;
    @Getter @Setter private long amount;

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(cloud, planUuid, accountPlanUuid, paymentMethodUuid, billUuid, amount);
}
