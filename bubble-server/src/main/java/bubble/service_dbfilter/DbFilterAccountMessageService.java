/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service_dbfilter;

import bubble.model.account.message.AccountMessage;
import bubble.service.account.AccountMessageService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterAccountMessageService implements AccountMessageService {

    @Override public boolean send(AccountMessage message) { return notSupported("send"); }

}
