package bubble.service.stream;

import java.util.Map;

import static java.util.Collections.emptyMap;

public interface RuleEngineService {

    default Map<Object, Object> flushCaches() { return emptyMap(); }

}
