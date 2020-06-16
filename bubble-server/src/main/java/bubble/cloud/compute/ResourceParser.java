/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;

public interface ResourceParser<E, C extends Collection<E>> {

    C newResults();

    E parse(JsonNode item);

    default boolean allowEmpty () { return false; }

}
