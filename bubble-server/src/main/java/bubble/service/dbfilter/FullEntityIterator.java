package bubble.service.dbfilter;

import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class FullEntityIterator extends EntityIterator {

    private final BubbleConfiguration config;
    private final BubbleNetwork network;

    public FullEntityIterator (BubbleConfiguration config,
                               BubbleNetwork network,
                               AtomicReference<Exception> error) {
        super(error);
        this.config = config;
        this.network = network;
    }

    protected void iterate() {
        config.getEntityClasses().forEach(c -> {
            addEntities(c, config.getDaoForEntityClass(c).findAll(), network, null);
        });
        log.info("iterate: completed");
    }

}
