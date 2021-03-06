/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import bubble.model.bill.AccountPaymentMethod;
import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AccountRegistration extends Account {

    @Getter @Setter private String password;

    @Getter @Setter private String promoCode;
    public boolean hasPromoCode () { return !empty(promoCode); }

    @Getter @Setter private Boolean agreeToTerms = null;
    public boolean agreeToTerms () { return agreeToTerms != null && agreeToTerms; }

    @Getter @Setter private Boolean receiveInformationalMessages = null;
    public boolean receiveInformationalMessages () { return receiveInformationalMessages != null && receiveInformationalMessages; }

    @Getter @Setter private Boolean receivePromotionalMessages = null;
    public boolean receivePromotionalMessages () { return receivePromotionalMessages != null && receivePromotionalMessages; }

    @Getter @Setter private AccountPaymentMethod paymentMethodObject;
    public boolean hasPaymentMethod () { return paymentMethodObject != null; }
}
