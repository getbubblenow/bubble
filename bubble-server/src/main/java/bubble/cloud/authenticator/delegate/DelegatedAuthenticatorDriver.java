/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.authenticator.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.authenticator.AuthenticatorServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.authenticator.AuthenticatorDriverNotification;

import static bubble.model.cloud.notify.NotificationType.authenticator_driver_send;

public class DelegatedAuthenticatorDriver extends DelegatedAuthDriverBase implements AuthenticatorServiceDriver {

    public DelegatedAuthenticatorDriver(CloudService cloud) { super(cloud); }

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, authenticator_driver_send, notification(new AuthenticatorDriverNotification()
                .setAccount(account)
                .setMessage(message)
                .setContact(contact)));
    }

}
