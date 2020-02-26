/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.sms.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.sms.SmsServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.email.EmailDriverNotification;

import static bubble.model.cloud.notify.NotificationType.sms_driver_send;

public class DelegatedSmsDriver extends DelegatedAuthDriverBase implements SmsServiceDriver {

    public DelegatedSmsDriver(CloudService cloud) { super(cloud); }

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, sms_driver_send, notification(new EmailDriverNotification()
                .setAccount(account)
                .setMessage(message)
                .setContact(contact)));
    }

}
