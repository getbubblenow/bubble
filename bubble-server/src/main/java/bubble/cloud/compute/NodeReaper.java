package bubble.cloud.compute;

import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class NodeReaper extends SimpleDaemon {

    private static final long STARTUP_DELAY = MINUTES.toMillis(30);
    private static final long KILL_CHECK_INTERVAL = MINUTES.toMillis(30);

    private final ComputeServiceDriverBase compute;

    public NodeReaper(ComputeServiceDriverBase compute) { this.compute = compute; }

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return KILL_CHECK_INTERVAL; }

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    private String prefix() { return compute.getClass().getSimpleName()+": "; }

    @Override protected void process() {
        try {
            compute.listNodes().forEach(this::processNode);
        } catch (Exception e) {
            log.warn(prefix()+"process: "+e);
            sleep(getSleepTime(), "waiting to process after exception: "+e);
        }
    }

    private void processNode(BubbleNode node) {
        final BubbleNode found = nodeDAO.findByIp4(node.getIp4());
        if (found == null) {
            if (wouldKillSelf(node)) return;
            log.warn(prefix()+"processNode: no node exists with ip4="+node.getIp4()+", killing it");
            final BubbleDomain domain = domainDAO.findByUuid(node.getDomain());
            final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
            try {
                dns.getDnsDriver(configuration).deleteNode(node);
                compute.stop(node);

            } catch (Exception e) {
                log.error(prefix()+"processNode: error stopping node "+node.getIp4()+": "+e);
            }
        }
    }

    private boolean wouldKillSelf(BubbleNode node) {
        if (node.hasSameIp(configuration.getThisNode())) {
            log.debug("processNode: not killing configuration.thisNode: "+node.getIp4()+"/"+node.getIp6());
            return true;
        }
        final Set<String> localIps = NetworkUtil.configuredIps();
        if (localIps.contains(node.getIp4()) || localIps.contains(node.getIp6())) {
            log.debug("processNode: not killing self, IP matches one of: "+ StringUtil.toString(localIps));
            return true;
        }
        return false;
    }

}
