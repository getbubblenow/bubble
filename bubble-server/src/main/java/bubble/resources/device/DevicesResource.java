/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.device;

import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.model.device.DeviceSecurityLevel;
import bubble.resources.account.AccountOwnedResource;
import bubble.resources.account.VpnConfigResource;
import bubble.server.BubbleConfiguration;
import bubble.service.device.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.model.device.DeviceStatusFirstComparator.DEVICE_WITH_STATUS_FIRST;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class DevicesResource extends AccountOwnedResource<Device, DeviceDAO> {

    @Autowired protected BubbleConfiguration configuration;

    public DevicesResource(Account account) { super(account); }

    @Override protected Device find(ContainerRequest ctx, String id) {
        final Device device = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        return device == null || device.uninitialized() ? null : device;
    }

    @Override protected List<Device> list(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final List<Device> devices;
        if (caller.admin() && ctx.getRequestUri().getQuery() != null && ctx.getRequestUri().getQuery().contains("all")) {
            devices = getDao().findAll().stream().filter(Device::initialized).collect(Collectors.toList());
        } else {
            devices = super.list(ctx).stream().filter(Device::initialized).collect(Collectors.toList());
        }
        return devices;
    }

    @Override protected Device populate(ContainerRequest ctx, Device device) {
        return device.setStatus(deviceService.getDeviceStatus(device.getUuid()));
    }

    @Override protected List<Device> sort(List<Device> list, Request req, ContainerRequest ctx) {
        list.sort(DEVICE_WITH_STATUS_FIRST);
        return list;
    }

    @Override protected boolean canChangeName() { return true; }

    @Override protected Device setReferences(ContainerRequest ctx, Account caller, Device device) {
        final String accountUuid = getAccountUuid(ctx);
        final String networkUuid = configuration.getThisNetwork().getUuid();
        device.setAccount(accountUuid).setNetwork(networkUuid);

        // check for name collisions
        final Device existingByAccount = getDao().findByAccountAndName(accountUuid, device.getName());
        if (existingByAccount != null && (!device.hasUuid() || !existingByAccount.getUuid().equals(device.getUuid()))) {
            throw invalidEx("err.deviceName.notUnique", "A device exists with that name", device.getName());
        }
        final Device existingByNetwork = getDao().findByNetworkAndName(networkUuid, device.getName());
        if (existingByNetwork != null && (!device.hasUuid() || !existingByNetwork.getUuid().equals(device.getUuid()))) {
            throw invalidEx("err.deviceName.notUnique", "A device exists with that name", device.getName());
        }

        if (!device.hasUuid()) {
            final String userAgent = getUserAgent(ctx);
            if (empty(userAgent)) throw invalidEx("err.userAgent.required", "User-Agent header was empty");
            device.initTotpKey();
        }

        // always set to default level upon registration. user can change later.
        final DeviceSecurityLevel defaultSecurityLevel = deviceService.getDefaultSecurityLevel(device.getDeviceType());
        device.setSecurityLevel(defaultSecurityLevel);
        log.info("setReferences: set securityLevel="+defaultSecurityLevel+" for device: "+device+" with type="+device.getDeviceType());

        return super.setReferences(ctx, caller, device);
    }

    @POST @Path("/{id}"+EP_SECURITY_LEVEL+"/{level}")
    public Response getIps(@Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("level") DeviceSecurityLevel level) {
        final Device device = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (device == null) return notFound(id);
        return ok(getDao().update(device.setSecurityLevel(level)));
    }

    @Path("/{id}"+EP_VPN)
    public VpnConfigResource getVpnConfig(@Context ContainerRequest ctx,
                                          @PathParam("id") String id) {
        final Device device = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        return configuration.subResource(VpnConfigResource.class, device);
    }

    @Autowired private DeviceService deviceService;

    @GET @Path("/{id}"+EP_IPS)
    public Response getIps(@Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        final Device device = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (device == null) return notFound(id);
        return ok(deviceService.findIpsByDevice(device.getUuid()));
    }

    @GET @Path("/{id}"+EP_STATUS)
    public Response getStatus(@Context ContainerRequest ctx,
                              @PathParam("id") String id) {
        final Device device = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (device == null) return notFound(id);
        return ok(deviceService.getLiveDeviceStatus(device.getUuid()));
    }

}
