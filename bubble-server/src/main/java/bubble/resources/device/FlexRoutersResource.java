/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.device;

import bubble.dao.device.FlexRouterDAO;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.model.device.FlexRouter;
import bubble.resources.account.AccountOwnedResource;
import bubble.service.device.DeviceService;
import bubble.service.device.FlexRouterStatus;
import bubble.service.device.StandardFlexRouterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.network.PortPicker;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.math.BigInteger;
import java.net.InetAddress;

import static bubble.ApiConstants.API_TAG_DEVICES;
import static bubble.ApiConstants.EP_STATUS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.network.PortPicker.portIsAvailable;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Slf4j
public class FlexRoutersResource extends AccountOwnedResource<FlexRouter, FlexRouterDAO> {

    @Autowired private DeviceService deviceService;
    @Autowired private StandardFlexRouterService flexRouterService;

    public FlexRoutersResource(Account account) { super(account); }

    @Override protected boolean isReadOnly(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return !caller.admin();
    }

    @Override protected FlexRouter findAlternate(ContainerRequest ctx, FlexRouter request) {
        return getDao().findByKeyHash(request.getKeyHash());
    }

    @GET @Path("/{id}"+EP_STATUS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_DEVICES,
            summary="Get flex router status",
            description="Get flex router status. The `id` param can be the UUID or IP address of the flex router. Returns the status of the router, which can be one of: `none`, `active`, `unreachable`, `deleted`",
            parameters=@Parameter(name="id", description="The UUID or IP address of the flex router"),
            responses=@ApiResponse(responseCode=SC_OK, description="status of the router, which can be one of: `none`, `active`, `unreachable`, `deleted`")
    )
    public Response getStatus(@Context Request req,
                              @Context ContainerRequest ctx,
                              @PathParam("id") String id) {
        FlexRouter router = find(req, ctx, id);
        if (router == null) router = findAlternate(req, ctx, id);
        if (router == null) return ok(FlexRouterStatus.deleted);
        return ok(flexRouterService.status(router.getUuid()));
    }

    @Override protected Object daoCreate(FlexRouter toCreate) {
        toCreate.setRegistered(true);
        final Object router = super.daoCreate(toCreate);
        sleep(SECONDS.toMillis(12), "waiting for refresh_flex_keys_monitor to write new flex SSH key");
        flexRouterService.interruptSoon();
        return router;
    }

    @Override protected Object daoUpdate(FlexRouter toUpdate) {
        toUpdate.setRegistered(true);
        final Object router = super.daoUpdate(toUpdate);
        flexRouterService.interruptSoon();
        return router;
    }

    @Override protected FlexRouter setReferences(ContainerRequest ctx, Request req, Account caller, FlexRouter router) {

        if (!router.hasKey()) throw invalidEx("err.sshPublicKey.required");

        final String ip = router.getIp();
        final Device device = deviceService.findDeviceByIp(ip);
        if (device == null) throw invalidEx("err.device.notFound");
        router.setDevice(device.getUuid());

        if (!router.hasAuthToken()) throw invalidEx("err.token.required");
        router.setToken(router.getAuth_token());

        if (!router.hasPort()) {
            // choose a port that no other FlexRouter is using, and that is available
            final InetAddress inetAddress;
            try {
                inetAddress = InetAddress.getByName(ip);
            } catch (Exception e) {
                throw invalidEx("err.ip.invalid");
            }
            final BigInteger addrInt = new BigInteger(inetAddress.getAddress());
            final int portOffset = addrInt.mod(new BigInteger("10000", 10)).intValueExact();
            boolean foundPort = false;
            for (int base = 20000; base < 65535; base += 10000) {
                final int port = base + portOffset;
                if (getDao().findByPort(port) == null && portIsAvailable(port)) {
                    router.setPort(port);
                    foundPort = true;
                    break;
                }
            }
            if (!foundPort) {
                final int port = PortPicker.pickOrDie();
                log.warn("setReferences: standard port could not be found, using port " + port + " from PortPicker");
                router.setPort(port);
            }
        } else if (!router.hasUuid()) {
            throw invalidEx("err.port.cannotSetOrChange");
        }
        router.setActive(false);
        return super.setReferences(ctx, req, caller, router);
    }

}
