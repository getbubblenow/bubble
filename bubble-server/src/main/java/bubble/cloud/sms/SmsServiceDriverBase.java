/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.sms;

import bubble.cloud.auth.RenderedMessage;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
        return SmsServiceDriver.send(this, account, message, contact);
    }

    @Override public boolean send(RenderedMessage renderedMessage) {
        final RenderedSms sms = (RenderedSms) renderedMessage;
        return deliver(sms.setFromNumber(getFromPhone()));
    }
}
