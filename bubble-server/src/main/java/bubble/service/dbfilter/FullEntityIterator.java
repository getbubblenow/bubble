package bubble.service.dbfilter;

import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FullEntityIterator extends EntityIterator {

    private final BubbleConfiguration config;

    public FullEntityIterator (BubbleConfiguration config) {
        this.config = config;
    }

    protected void iterate() {
        config.getEntityClasses().forEach(c -> {
            addEntities(c, config.getDaoForEntityClass(c).findAll(), null);
        });
        log.info("iterate: completed");
    }

}
