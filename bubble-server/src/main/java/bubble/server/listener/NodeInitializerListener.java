package bubble.server.listener;

import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SelfNodeService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import java.io.File;

import static bubble.service.boot.StandardSelfNodeService.SELF_NODE_JSON;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss;

@Slf4j
public class NodeInitializerListener extends RestServerLifecycleListenerBase<BubbleConfiguration> {

    @Override public void beforeStart(RestServer server) {
        final BubbleConfiguration c = (BubbleConfiguration) server.getConfiguration();

        // ensure we can reference our own jar file
        final File bubbleJar = c.getBubbleJar();
        if (bubbleJar == null || !bubbleJar.exists()) die("beforeStart: bubble jar file not found: "+abs(bubbleJar));

        // ensure locales were loaded correctly
        final String[] allLocales = c.getAllLocales();
        if (empty(allLocales)) die("beforeStart: no locales found");  // should never happen
    }

    @Override public void onStart(RestServer server) {
        final BubbleConfiguration c = (BubbleConfiguration) server.getConfiguration();

        if (!c.getBean(AccountDAO.class).activated()) {
            final File nodeFile = THIS_NODE_FILE;
            if (nodeFile.exists()) {
                final File backupSelfNode = new File(abs(nodeFile) + ".backup_" + DATE_FORMAT_YYYY_MM_DD_HH_mm_ss.print(now()));
                if (!nodeFile.renameTo(backupSelfNode)) {
                    die("onStart: error renaming "+SELF_NODE_JSON+" -> "+abs(backupSelfNode)+" for un-activated bubble");
                }
            }
            log.info("onStart: renamed "+SELF_NODE_JSON+" for un-activated bubble");
        }

        final BubbleNode thisNode = c.getThisNode();
        if (thisNode != null) {
            c.getBean(SelfNodeService.class).initThisNode(thisNode);
        } else {
            log.warn("onStart: thisNode was null, not doing standard initializations");
        }

        // warm up drivers
        final Account admin = c.getBean(AccountDAO.class).getFirstAdmin();
        if (admin != null) {
            for (CloudService cloud : c.getBean(CloudServiceDAO.class).findPublicTemplates(admin.getUuid())) {
                try {
                    cloud.wireAndSetup(c);
                } catch (Exception e) {
                    log.warn("onStart: error initializing driver for cloud: "+cloud.getName()+"/"+cloud.getUuid()+": "+e);
                }
//            background(() -> cloud.wireAndSetup(c));
            }
        }

        log.info("onStart: completed");
    }

}
