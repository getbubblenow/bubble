package bubble.dao.app;

import com.fasterxml.jackson.databind.JsonNode;

public interface RemoteDAO {

    default void configure (JsonNode config) {}

}
