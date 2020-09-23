package bubble.resources.device;

import bubble.model.account.Account;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.DeviceSecurityLevel;
import bubble.service.device.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static bubble.ApiConstants.EP_DEFAULT_SECURITY_LEVEL;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Slf4j
public class DeviceTypesResource {

    @Autowired private DeviceService deviceService;

    private Account account;

    public DeviceTypesResource (Account account) { this.account = account; }

    @GET
    public Response getDeviceTypes (@Context ContainerRequest ctx) {
        return ok(BubbleDeviceType.getSelectableTypes());
    }

    @GET @Path(EP_DEFAULT_SECURITY_LEVEL)
    public Response getDefaultSecurityLevels (@Context ContainerRequest ctx) {
        return ok(getDefaultSecurityLevels());
    }

    public Map<BubbleDeviceType, DeviceSecurityLevel> getDefaultSecurityLevels() {
        final Map<BubbleDeviceType, DeviceSecurityLevel> levels = new HashMap<>();
        for (BubbleDeviceType type : BubbleDeviceType.getSelectableTypes()) {
            levels.put(type, deviceService.getDefaultSecurityLevel(type));
        }
        return levels;
    }

    @POST @Path(EP_DEFAULT_SECURITY_LEVEL+"/{deviceType}/{level}")
    public Response setDefaultSecurityLevel (@Context ContainerRequest ctx,
                                             @PathParam("deviceType") BubbleDeviceType type,
                                             @PathParam("level") DeviceSecurityLevel level) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        deviceService.setDefaultSecurityLevel(type, level);
        return ok(getDefaultSecurityLevels());
    }

}
