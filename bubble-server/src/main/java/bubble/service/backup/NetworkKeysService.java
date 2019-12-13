package bubble.service.backup;

import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.NetworkKeys;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE_CREDENTIALS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.json.JsonUtil.json;

@Service @Slf4j
public class NetworkKeysService {

    public static final long KEY_EXPIRATION = MINUTES.toSeconds(15);

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService networkPasswordTokens = redis.prefixNamespace(getClass().getSimpleName());

    public void registerView(String uuid) {
        final NetworkKeys keys = new NetworkKeys();
        final CloudService storage = cloudDAO.findByUuid(configuration.getThisNetwork().getStorage());
        if (storage == null) {
            log.warn("network storage was null!");
        } else {
            keys.addKey(PARAM_STORAGE, json(storage));
            keys.addKey(PARAM_STORAGE_CREDENTIALS, json(storage.getCredentials()));
        }
        getNetworkPasswordTokens().set(uuid, json(keys), "EX", KEY_EXPIRATION);
    }

    public NetworkKeys retrieveKeys(String uuid) {
        final String json = getNetworkPasswordTokens().get(uuid);
        if (json == null) return null;
        getNetworkPasswordTokens().del(uuid);
        return json(json, NetworkKeys.class);
    }

}
