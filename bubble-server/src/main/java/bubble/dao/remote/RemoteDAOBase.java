package bubble.dao.remote;

import bubble.client.BubbleApiClient;
import bubble.dao.app.RemoteDAO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.http.ApiConnectionInfo;

import static org.cobbzilla.util.json.JsonUtil.json;

public class RemoteDAOBase implements RemoteDAO {

    @Getter @Setter private RemoteDAOConfig config;

    @Override public void configure(JsonNode config) {
        this.config = json(json(config), RemoteDAOConfig.class);
    }

    @Getter(lazy=true) private final BubbleApiClient api = initApi();
    private BubbleApiClient initApi() {
        final BubbleApiClient api = new BubbleApiClient(new ApiConnectionInfo(config.getUriBase()));
        api.setToken(config.getSessionId());
        return api;
    }

}
