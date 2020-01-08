package bubble.service.dbfilter;

import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
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

    public void addEntities(Class<? extends Identifiable> c, List<? extends Identifiable> entities, BubbleNode node) {
        if (CloudService.class.isAssignableFrom(c)) {
            entities.forEach(e -> add(setLocalStoragePath((CloudService) e)));
        } else {
            entities.forEach(this::add);
        }
    }

    public CloudService setLocalStoragePath(CloudService cloudService) {
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
