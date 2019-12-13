package bubble.service.account.download;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.server.BubbleConfiguration;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.daemon;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;

@Accessors(chain=true) @Slf4j
public class AccountDownloadCollector implements Runnable {

    private BubbleConfiguration configuration;
    private String accountUuid;
    private AtomicReference<Map<String, List<String>>> ref;
    private String remoteHost;
    @Setter private Thread thread;
    private AccountMessageDAO messageDAO;
    private boolean sendMessage;
    private AccountDownloadService downloadService;

    public AccountDownloadCollector(BubbleConfiguration configuration,
                                    String accountUuid,
                                    AtomicReference<Map<String, List<String>>> ref,
                                    String remoteHost,
                                    AccountMessageDAO messageDAO,
                                    boolean sendMessage,
                                    AccountDownloadService downloadService) {
        this.configuration = configuration;
        this.accountUuid = accountUuid;
        this.ref = ref;
        this.remoteHost = remoteHost;
        this.messageDAO = messageDAO;
        this.sendMessage = sendMessage;
        this.downloadService = downloadService;
    }

    @Override public void run() {
        try {
            AbstractCRUDDAO.getRawMode().set(true);
            final Map<String, List<String>> data = new HashMap<>();
            configuration.getEntityClasses().forEach(clazz -> collectEntities(data, clazz, configuration, accountUuid));
            ref.set(data);
            if (sendMessage) {
                daemon(new AccountDownloadMonitor(downloadService, thread, ref, accountUuid, messageDAO, remoteHost));
            }
        } catch (Exception e) {
            // todo: add exception handler that sends to Errbit
            die("error: "+e, e);
        }
    }

    public static void collectEntities(Map<String, List<String>> data,
                                       Class<? extends Identifiable> clazz,
                                       BubbleConfiguration configuration,
                                       String accountUuid) {
        final DAO dao = configuration.getDaoForEntityClass(clazz);
        if (AccountOwnedEntityDAO.class.isAssignableFrom(dao.getClass())) {
            ((AccountOwnedEntityDAO) dao).findByAccount(accountUuid).forEach(e -> addEntity(data, clazz, e));
        } else if (dao instanceof AccountDAO) {
            data.computeIfAbsent(clazz.getSimpleName(), k -> new ArrayList<>()).add(json(dao.findByUuid(accountUuid)));
        }
    }

    public static void addEntity(Map<String, List<String>> data, Class<? extends Identifiable> clazz, Object e) {
        sleep(5, "downloadAccountData: checking for interruption");
        data.computeIfAbsent(clazz.getSimpleName(), k -> new ArrayList<>()).add(json(e));
    }
}
