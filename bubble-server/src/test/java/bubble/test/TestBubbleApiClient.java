package bubble.test;

import bubble.client.BubbleApiClient;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.wizard.auth.AuthResponse;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.util.Map;

import static bubble.ApiConstants.AUTH_ENDPOINT;
import static bubble.ApiConstants.EP_LOGIN;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class TestBubbleApiClient extends BubbleApiClient {

    @Getter @Setter private RestServerConfiguration configuration;

    @SuppressWarnings("unused") // called by ApiClientBase.copy
    public TestBubbleApiClient(TestBubbleApiClient other) {
        super(other.getConnectionInfo());
        setConfiguration(other.getConfiguration());
        setToken(other.getToken());
    }

    @SuppressWarnings("unused") // called by ApiScript.getConnection
    public TestBubbleApiClient(ApiConnectionInfo info) { super(info); }

    public TestBubbleApiClient(RestServerConfiguration configuration) {
        super(new ApiConnectionInfo(configuration.getApiUriBase()));
        setConfiguration(configuration);
    }

    @Getter(lazy=true) private final String superuserToken = initSuperuserToken();
    private String initSuperuserToken() {
        final Map<String, String> env = configuration.getEnvironment();
        final LoginRequest login = new LoginRequest(env.get("BUBBLE_SUPERUSER"), env.get("BUBBLE_SUPERUSER_PASS"));
        try {
            return post(AUTH_ENDPOINT+EP_LOGIN, json(login), AuthResponse.class).getSessionId();
        } catch (Exception e) {
            return die("initSuperuserToken: "+e, e);
        }
    }

}
