/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.account.download;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.service.account.download.AccountDownloadService.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.terminate;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@AllArgsConstructor @Slf4j
public class AccountDownloadMonitor implements Runnable {

    private AccountDownloadService downloadService;
    private Thread t;
    private AtomicReference<Map<String, List<String>>> ref;
    private String accountUuid;
    private String networkUuid;
    private AccountMessageDAO messageDAO;
    private String remoteHost;

    @Override public void run() {
        try {
            final Map<String, List<String>> data = waitForData(t, ref);
            if (data == null) {
                log.error("downloadAccountData: no data returned (timeout?)");
            } else {
                final AccountMessage message = messageDAO.create(new AccountMessage()
                        .setAccount(accountUuid)
                        .setName(accountUuid)
                        .setNetwork(networkUuid)
                        .setMessageType(AccountMessageType.request)
                        .setAction(AccountAction.download)
                        .setTarget(ActionTarget.account)
                        .setRemoteHost(remoteHost));
                downloadService.getAccountData().set(message.getRequestId(), json(data), EX, ACCOUNT_DOWNLOAD_EXPIRATION);
            }
        } catch (Exception e) {
            die("error: "+e, e);
        }
    }

    public static Map<String, List<String>> waitForData(Thread t, AtomicReference<Map<String, List<String>>> ref) {
        try {
            t.join(DOWNLOAD_ACCOUNT_TIMEOUT);
            return ref.get();
        } catch (InterruptedException e) {
            log.error("waitForData: interrupted");
            return null;
        } finally {
            if (t.isAlive()) terminate(t, DOWNLOAD_TERMINATE_TIMEOUT);
        }
    }

}
