package bubble.service.account.download;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.grizzly.http.server.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.getRemoteHost;
import static bubble.service.account.download.AccountDownloadMonitor.waitForData;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.json.JsonUtil.json;

@Service @Slf4j
public class AccountDownloadService {

    public static final long ACCOUNT_DOWNLOAD_EXPIRATION = MINUTES.toSeconds(30);
    public static final long DOWNLOAD_ACCOUNT_TIMEOUT = MINUTES.toMillis(2);
    public static final long DOWNLOAD_TERMINATE_TIMEOUT = SECONDS.toMillis(10);

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountMessageDAO messageDAO;

    // todo: use a storage CloudService, write another service to delete expired data
    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService accountData = redis.prefixNamespace(getClass().getSimpleName()+"_pending");
    @Getter(lazy=true) private final RedisService approvedAccountData = redis.prefixNamespace(getClass().getSimpleName()+"_approved");

    public JsonNode retrieveAccountData(String uuid) {
        final String json = getApprovedAccountData().get(uuid);
        if (json == null) return null;
        cancel(uuid);  // only one download allowed per request
        return json(json, JsonNode.class);
    }

    public void approve(String uuid) {
        final String data = getAccountData().get(uuid);
        if (data != null) {
            getApprovedAccountData().set(uuid, data, "EX", ACCOUNT_DOWNLOAD_EXPIRATION);
        }
    }

    public void cancel(String uuid) {
        getApprovedAccountData().del(uuid);
        getAccountData().del(uuid);
    }

    public void downloadAccountData(Request req,
                                    String accountUuid) {
        downloadAccountData(req, accountUuid, true);
    }

    private static final Map<String, AccountDownloadRequest> activeDownloads = new ConcurrentHashMap<>();

    public Map<String, List<String>> downloadAccountData(Request req,
                                                         String accountUuid,
                                                         boolean sendMessage) {
        final String remoteHost = getRemoteHost(req);

        AccountDownloadRequest downloadRequest;
        synchronized (activeDownloads) {
            downloadRequest = activeDownloads.get(accountUuid);
            if (downloadRequest != null) {
                log.warn("downloadAccountData: download already in progress for "+accountUuid);

            } else {
                final AtomicReference<Map<String, List<String>>> ref = new AtomicReference<>();
                final AccountDownloadCollector collector = new AccountDownloadCollector(configuration, accountUuid, ref, remoteHost, messageDAO, sendMessage, this);
                final Thread t = new Thread(collector);
                collector.setThread(t);
                downloadRequest = new AccountDownloadRequest(t, ref);
                activeDownloads.put(accountUuid, downloadRequest);
                t.setDaemon(true);
                t.start();
            }
        }
        return waitForData(downloadRequest.t, downloadRequest.ref);
    }

    @AllArgsConstructor
    private static class AccountDownloadRequest {
        private Thread t;
        private AtomicReference<Map<String, List<String>>> ref;
    }
}
