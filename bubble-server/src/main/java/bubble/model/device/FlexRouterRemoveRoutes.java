package bubble.model.device;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Collection;

@Accessors(chain=true)
public class FlexRouterRemoveRoutes {

    @Getter private final FlexRouterPing ping;
    @Getter private final String[] routes;

    public FlexRouterRemoveRoutes (FlexRouter router, Collection<String> routes) {
        this.ping = router.pingObject();
        this.routes = routes.toArray(String[]::new);
    }

}
