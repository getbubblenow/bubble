/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.sms.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.sms.RenderedSms;
import bubble.cloud.sms.SmsConfig;
import bubble.cloud.sms.SmsServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.NotificationType;

import static bubble.model.cloud.notify.NotificationType.sms_driver_send;

public class DelegatedSmsDriver extends DelegatedAuthDriverBase implements SmsServiceDriver {

    public DelegatedSmsDriver(CloudService cloud) { super(cloud); }

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        return SmsServiceDriver.send(this, account, message, contact);
    }

    @Override protected String getDefaultTemplatePath() { return SmsConfig.DEFAULT_TEMPLATE_PATH; }

    @Override protected NotificationType getSendNotificationType() { return sms_driver_send; }

    @Override protected Class<? extends RenderedMessage> getRenderedMessageClass() { return RenderedSms.class; }

}
