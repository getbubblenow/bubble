package bubble.service.cloud;

import bubble.notify.NewNodeNotification;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@AllArgsConstructor @Slf4j
public class NodeLauncher implements Runnable {

    private NewNodeNotification newNodeRequest;
    private AtomicReference<String> lock;
    private StandardNetworkService networkService;

    @Override public void run() {
        final String network = newNodeRequest.getNetwork();
        boolean locked = false;
        try {
            if (lock.get() == null) {
                lock.set(networkService.lockNetwork(network));
                locked = true;
            } else {
                if (!lock.get().equals(newNodeRequest.getLock())) {
                    die("backgroundNewNode: existingLock (" + lock.get() + ") is different than lock in NewNodeNotification: " + newNodeRequest.getLock());
                }
                networkService.confirmLock(network, lock.get());
            }
            networkService.newNode(newNodeRequest.setLock(lock.get()));
        } catch (Exception e) {
            log.error("error: "+e, e);
        } finally {
            if (locked && lock.get() != null) networkService.unlockNetwork(network, lock.get());
        }
    }

}
