package bubble.service.cloud;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class NetworkMonitorService extends SimpleDaemon {

    private static final long STARTUP_DELAY = MINUTES.toMillis(1);
    private static final long CHECK_INTERVAL = MINUTES.toMillis(30);

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return CHECK_INTERVAL; }

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private BubbleConfiguration configuration;

    @Override protected void process() {
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        try {
            for (BubbleNetwork network : networkDAO.findAll()) {
                if (thisNetwork != null && network.getUuid().equals(thisNetwork.getUuid())) {
                    continue;
                }
                if (networkService.anyNodesRunning(network)) {
                    switch (network.getState()) {
                        case running: case restoring: continue;
                        default:
                            reportError(getName()+": network "+network.getNetworkDomain()+" has nodes running but state is "+network.getState());
                    }
                } else {
                    if (network.getState() != BubbleNetworkState.stopped && network.getState() != BubbleNetworkState.error_stopping) {
                        reportError(getName() + ": network " + network.getNetworkDomain() + " does NOT have nodes running but state is " + network.getState()+", marking it 'error_stopping'");
                        networkDAO.update(network.setState(BubbleNetworkState.error_stopping));
                    }
                }
            }

        } catch (Exception e) {
            reportError(getName()+": fatal error: "+shortError(e), e);
        }
    }
}
