package bubble.cloud.compute;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;

public interface ResourceParser<E, C extends Collection<E>> {

    C newResults();

    E parse(JsonNode item);

    default boolean allowEmpty () { return false; }

}
