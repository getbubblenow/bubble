/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.message.AccountMessage;

public interface AccountMessageService {

    boolean send(AccountMessage message);

}
