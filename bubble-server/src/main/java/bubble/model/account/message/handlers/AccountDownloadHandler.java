/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message.handlers;

import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import bubble.service.account.download.AccountDownloadService;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.beans.factory.annotation.Autowired;

public class AccountDownloadHandler implements AccountMessageCompletionHandler {

    @Autowired private AccountDownloadService downloadService;

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        downloadService.approve(message.getRequestId());
    }

    @Override public void deny(AccountMessage message) {
        downloadService.cancel(message.getRequestId());
    }

}
