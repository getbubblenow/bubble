/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import java.util.Map;

import static java.util.Collections.emptyMap;

public interface RuleEngineService {

    default Map<Object, Object> flushCaches() { return emptyMap(); }
    default Map<Object, Object> flushCaches(boolean prime) { return emptyMap(); }

}
