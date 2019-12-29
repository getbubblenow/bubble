package bubble.service.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Service
public class RequestCoordinationService {

    @Autowired private RedisService redisService;
    @Getter(lazy=true) private final RedisService requests = redisService.prefixNamespace(getClass().getSimpleName()+"_");

    public void set(String prefix, String id, JsonNode thing) {
        getRequests().set(prefix+":"+id, json(thing), EX, 600);
    }

    public void set(String prefix, String id, String thing) {
        getRequests().set(prefix+":"+id, thing, EX, 600);
    }

    public String get(String prefix, String id) {
        return getRequests().get(prefix+":"+id);
    }

}
