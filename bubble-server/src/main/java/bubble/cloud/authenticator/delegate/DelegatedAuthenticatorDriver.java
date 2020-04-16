/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.authenticator.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.authenticator.AuthenticatorServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.NotificationType;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public class DelegatedAuthenticatorDriver extends DelegatedAuthDriverBase implements AuthenticatorServiceDriver {

    public DelegatedAuthenticatorDriver(CloudService cloud) { super(cloud); }

    @Override protected String getDefaultTemplatePath() { return notSupported("getDefaultTemplatePath"); }
    @Override protected NotificationType getSendNotificationType() { return notSupported("getSendNotificationType"); }
    @Override protected Class<? extends RenderedMessage> getRenderedMessageClass() { return notSupported("getRenderedMessageClass"); }
    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) { return notSupported("send"); }

}
