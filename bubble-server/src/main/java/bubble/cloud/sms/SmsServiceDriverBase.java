/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.sms;

import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.LocaleUtil;

import java.util.Map;

import static bubble.cloud.auth.SmsAuthFieldHandler.formatPhoneForCountry;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;

@Slf4j
public abstract class SmsServiceDriverBase<T extends SmsConfig> implements SmsServiceDriver {

    protected T config;

    @Override public void setConfig(JsonNode json, CloudService cloudService) {
        config = json(json, getFirstTypeParam(getClass()));
    }

    @Getter @Setter private CloudCredentials credentials;

    @Override public String getTemplatePath() { return config.getTemplatePath(); }

    protected abstract String getFromPhone();

    protected abstract boolean deliver(RenderedSms sms);

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {

        final String info = contact.getInfo();
        final int colonPos = info.indexOf(':');
        if (colonPos == -1 || colonPos == info.length()-1) return die("send: invalid number: "+ info);

        final String country = info.substring(0, colonPos);
        final String toPhone = formatPhoneForCountry(country, info.substring(colonPos+1));
        final String prefix = LocaleUtil.getPhoneCode(country);
        if (prefix == null) return die("send: no telephone prefix found for country: "+country);

        final String dest = "+" + prefix + toPhone;
        final Map<String, Object> ctx = buildContext(account, message, contact);
        final String text = render("message", ctx, message);

        return deliver(new RenderedSms(ctx)
                .setFromNumber(getFromPhone())
                .setToNumber(dest)
                .setText(text));
    }

}
