/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.stream;

import java.util.Map;

import static java.util.Collections.emptyMap;

public interface RuleEngineService {

    default Map<Object, Object> flushCaches() { return emptyMap(); }

}
