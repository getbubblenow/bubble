/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.model.device.FlexRouter;

public interface FlexRouterService {

    default void register (FlexRouter router) {}
    default void unregister (FlexRouter router) {}
    default void interruptSoon () {}

}
