/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.sms;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import org.cobbzilla.util.string.LocaleUtil;

import java.util.Map;

import static bubble.cloud.auth.SmsAuthFieldHandler.formatPhoneForCountry;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public interface SmsServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.sms; }

    static boolean send(SmsServiceDriver driver, Account account, AccountMessage message, AccountContact contact) {
        final String info = contact.getInfo();
        final int colonPos = info.indexOf(':');
        if (colonPos == -1 || colonPos == info.length()-1) return die("send: invalid number: "+ info);

        final String country = info.substring(0, colonPos);
        final String toPhone = formatPhoneForCountry(country, info.substring(colonPos+1));
        final String prefix = LocaleUtil.getPhoneCode(country);
        if (prefix == null) return die("send: no telephone prefix found for country: "+country);

        final String dest = "+" + prefix + toPhone;
        final Map<String, Object> ctx = driver.buildContext(account, message, contact);
        final String text = driver.render("message", ctx, message);

        final RenderedSms renderedSms = new RenderedSms(ctx)
                .setToNumber(dest)
                .setText(text);

        return driver.send(renderedSms);
    }

}
