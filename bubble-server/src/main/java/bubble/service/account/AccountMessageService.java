/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.Account;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import org.cobbzilla.util.collection.NameAndValue;

public interface AccountMessageService {

    boolean send(AccountMessage message);

    AccountMessage captureResponse(Account account,
                                   String remoteHost,
                                   String token,
                                   AccountMessageType type,
                                   NameAndValue[] data);
}
