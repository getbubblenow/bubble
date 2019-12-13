package bubble.resources.driver;

import bubble.dao.app.AppDataDAO;
import bubble.model.app.AppData;
import bubble.rule.RuleConfig;
import bubble.rule.RuleConfigBase;
import bubble.rule.AppRuleDriver;
import bubble.service.cloud.RequestCoordinationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.stream.ByteStreamingOutput;
import org.cobbzilla.wizard.stream.SendableResource;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.EP_ASSETS;
import static bubble.ApiConstants.EP_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.contentType;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS_AND_UNKNOWN_FIELDS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class DriverAssetsResource {

    @Autowired private AppDataDAO dataDAO;
    @Autowired private RequestCoordinationService requestService;

    @Getter private String requestId;
    @Getter private String driverClass;
    @Getter(lazy=true) private final String json = requestService.get(getDriverClass(), getRequestId());

    public DriverAssetsResource (String requestId, String driverClass) {
        this.requestId = requestId;
        this.driverClass = driverClass;
    }

    @Getter(lazy=true) private final AppRuleDriver driver = instantiate(getDriverClass());

    @GET @Path(EP_ASSETS+"/{path}")
    public Response get(@Context ContainerRequest ctx,
                        @PathParam("path") String path) {
        final byte[] bytes = getDriver().locateBinaryResource(path);
        if (bytes == null) return notFound(path);
        return send(new SendableResource(new ByteStreamingOutput(bytes))
                .setContentType(contentType(path))
                .setContentLength((long) bytes.length));
    }

    @Getter(lazy=true) private final RuleConfig ruleConfig = initRuleConfig();
    private RuleConfig initRuleConfig() {
        try {
            return json(getJson(), RuleConfigBase.class, FULL_MAPPER_ALLOW_COMMENTS_AND_UNKNOWN_FIELDS);
        } catch (Exception e) {
            return die("initRuleConfig: "+e);
        }
    }

    @GET @Path(EP_DATA)
    public Response get(@Context ContainerRequest ctx,
                        @QueryParam("key") String key,
                        @QueryParam("value") String value) {
        final RuleConfig config = getRuleConfig();
        return ok(dataDAO.set(new AppData(config).setKey(key).setData(value)));
    }

}
