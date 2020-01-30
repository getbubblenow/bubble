package bubble.service.dbfilter;

import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.model.account.AccountSshKey;
import bubble.model.app.AppTemplateEntity;
import bubble.model.app.BubbleApp;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import org.cobbzilla.wizard.model.Identifiable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE_STANDARD_BASE_DIR;
import static bubble.service.dbfilter.EndOfEntityStream.END_OF_ENTITY_STREAM;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;

public abstract class EntityIterator implements Iterator<Identifiable> {

    private static final int MAX_QUEUE_SIZE = 100;

    private final BlockingQueue<Identifiable> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    @Getter private final Thread thread;
    @Getter private final AtomicReference<Exception> error;

    public EntityIterator(AtomicReference<Exception> error) {
        this.error = error;
        this.thread = background(this::_iterate, this.error::set);
    }

    @Override public boolean hasNext() {
        checkError();
        return !(queue.peek() instanceof EndOfEntityStream);
    }

    @Override public Identifiable next() {
        checkError();
        if (!hasNext()) {
            throw new NoSuchElementException("iterator has been exhausted");
        }
        try {
            return queue.take();
        } catch (InterruptedException e) {
            error.set(e);
            return die("next: queue.take interrupted");
        }
    }

    private void _iterate() {
        iterate();
        add(END_OF_ENTITY_STREAM);
    }

    protected abstract void iterate();

    protected void add(Identifiable from) {
        try {
            queue.put(from);
        } catch (InterruptedException e) {
            error.set(e);
            die("add: queue.put interrupted");
        }
    }

    public void addEntities(Class<? extends Identifiable> c,
                            List<? extends Identifiable> entities,
                            BubbleNetwork network,
                            BubbleNode node,
                            List<BubblePlanApp> planApps) {
        if (CloudService.class.isAssignableFrom(c)) {
            entities.forEach(e -> add(setLocalStoragePath((CloudService) e)));

        } else if (AccountSshKey.class.isAssignableFrom(c)) {
            entities.forEach(e -> add(setInstallKey((AccountSshKey) e, network)));

        } else if (planApps != null && BubbleApp.class.isAssignableFrom(c)) {
            // only copy enabled apps
            entities.stream().filter(e -> planAppEnabled(e.getUuid(), planApps))
                    .forEach(this::add);

        } else if (planApps != null && AppTemplateEntity.class.isAssignableFrom(c)) {
            // only copy app-related entities for enabled apps
            entities.stream()
                    .filter(e -> planAppEnabled(((AppTemplateEntity) e).getApp(), planApps))
                    .forEach(this::add);

        } else {
            entities.forEach(this::add);
        }
    }

    private boolean planAppEnabled(String appUuid, List<BubblePlanApp> planApps) {
        return planApps == null || planApps.stream().anyMatch(planApp -> planApp.getApp().equals(appUuid));
    }

    private AccountSshKey setInstallKey(AccountSshKey sshKey, BubbleNetwork network) {
        if (network == null) return sshKey;
        if (network.hasSshKey() && network.getSshKey().equals(sshKey.getUuid())) {
            sshKey.setInstallSshKey(true);
        }
        return sshKey;
    }

    private CloudService setLocalStoragePath(CloudService cloudService) {
        if (!cloudService.usesDriver(LocalStorageDriver.class)) {
            return cloudService;
        }
        final LocalStorageConfig localConfig = json(cloudService.getDriverConfigJson(), LocalStorageConfig.class);
        return cloudService.setDriverConfigJson(json(localConfig.setBaseDir(LOCAL_STORAGE_STANDARD_BASE_DIR)));
    }

    private void checkError() {
        final Exception ex = error.get();
        if (ex != null) {
            if (ex instanceof RuntimeException) throw (RuntimeException) ex;
            die(getClass().getName()+": "+shortError(ex));
        }
    }

}
