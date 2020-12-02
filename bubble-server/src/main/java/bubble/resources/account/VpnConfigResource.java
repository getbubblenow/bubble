/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.model.account.Account;
import bubble.model.device.Device;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.wizard.stream.FileSendableResource;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;

import static bubble.ApiConstants.API_TAG_DEVICE;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Slf4j
public class VpnConfigResource {

    private Device device;

    public VpnConfigResource(Device device) { this.device = device; }

    public File getQRfile() {
        final File qrFile = device.qrFile();
        if (!qrFile.exists()) {
            // todo: try to regenerate algo users?
            log.error("qrCode: file not found: "+abs(qrFile));
            throw invalidEx("err.deviceQRcode.qrCodeFileNotFound");
        }
        return qrFile;
    }

    public File getVpnConfFile() {
        final File confFile = device.vpnConfFile();
        if (!confFile.exists()) {
            // todo: try to regenerate algo users?
            log.error("confFile: file not found: "+abs(confFile));
            throw invalidEx("err.deviceVpnConf.confFileNotFound");
        }
        return confFile;
    }

    @GET @Path("/QR.png")
    @Produces(IMAGE_PNG)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_DEVICE,
            summary="Get QR code PNG image for device",
            description="Get QR code PNG image for device",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="QR code PNG image data"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="if caller is not admin or does not own the device")
            }
    )
    public Response qrCode(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(device.getAccount())) return forbidden();
        return send(new FileSendableResource(getQRfile()));
    }

    @GET @Path("/QR.png.base64")
    @Produces(TEXT_PLAIN)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_DEVICE,
            summary="Get QR code PNG image, as Base64-encoded string",
            description="Get QR code PNG image, as Base64-encoded string",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="Base64-encoded QR code PNG image data"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="if caller is not admin or does not own the device")
            }
    )
    public Response qrCodeBase64(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(device.getAccount())) return forbidden();
        final String data;
        try {
            data = Base64.encodeBytes(FileUtil.toBytes(getQRfile()));
        } catch (IOException e) {
            return invalid("err.deviceQRcode.qrCodeError");
        }
        return ok(data);
    }

    @GET @Path("/vpn.conf")
    @Produces(APPLICATION_OCTET_STREAM)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_DEVICE,
            summary="Get WireGuard vpn.conf file for device",
            description="Get WireGuard vpn.conf file for device",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="vpn.conf file data"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="if caller is not admin or does not own the device")
            }
    )
    public Response confFile(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(device.getAccount())) return forbidden();

        final File confFile = getVpnConfFile();
        return send(new FileSendableResource(confFile)
                .setContentType(APPLICATION_OCTET_STREAM)
                .setForceDownload(true));
    }

    @GET @Path("/vpn.conf.base64")
    @Produces(TEXT_PLAIN)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_DEVICE,
            summary="Get WireGuard vpn.conf file, as Base64-encoded string",
            description="Get WireGuard vpn.conf file for device, as Base64-encoded string",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="Base64-encoded vpn.conf file data"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="if caller is not admin or does not own the device")
            }
    )
    public Response confFileBase64(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(device.getAccount())) return forbidden();

        final String data;
        try {
            data = Base64.encodeBytes(FileUtil.toBytes(getVpnConfFile()));
        } catch (IOException e) {
            return invalid("err.deviceVpnConf.confError");
        }
        return ok(data);
    }

}
