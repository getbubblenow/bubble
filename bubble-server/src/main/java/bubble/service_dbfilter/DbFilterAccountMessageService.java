/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.account.Account;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.service.account.AccountMessageService;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterAccountMessageService implements AccountMessageService {

    @Override public boolean send(AccountMessage message) { return notSupported("send"); }

    @Override public AccountMessage captureResponse(Account account, String remoteHost, String token, AccountMessageType type, NameAndValue[] data) {
        return notSupported("captureResponse");
    }

}
