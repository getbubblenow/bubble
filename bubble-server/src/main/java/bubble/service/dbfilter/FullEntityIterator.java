package bubble.service.dbfilter;

import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class FullEntityIterator extends EntityIterator {

    private final BubbleConfiguration config;

    public FullEntityIterator (BubbleConfiguration config, AtomicReference<Exception> error) {
        super(error);
        this.config = config;
    }

    protected void iterate() {
        config.getEntityClasses().forEach(c -> {
            addEntities(c, config.getDaoForEntityClass(c).findAll(), null, null);
        });
        log.info("iterate: completed");
    }

}
