/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.payment;

import bubble.model.bill.AccountPaymentMethod;
import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @Slf4j
public class PaymentMethodValidationNotification extends SynchronousNotification {

    @Getter @Setter private String cloud;
    @Getter @Setter private AccountPaymentMethod paymentMethod;

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(cloud, paymentMethod != null ? paymentMethod.getCacheKey() : null);

}
