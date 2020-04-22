/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.ApiConstants;
import bubble.model.account.Account;
import bubble.model.device.Device;
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

import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Slf4j
public class VpnConfigResource {

    public static final String VPN_CONFIG_PATH = ApiConstants.HOME_DIR + "/configs/localhost/wireguard/";

    private Device device;

    public VpnConfigResource(Device device) { this.device = device; }

    public File getQRfile() {
        final File qrFile = new File(VPN_CONFIG_PATH+device.getUuid()+".png");
        if (!qrFile.exists()) {
            // todo: try to regenerate algo users?
            log.error("qrCode: file not found: "+abs(qrFile));
            throw invalidEx("err.deviceQRcode.qrCodeFileNotFound");
        }
        return qrFile;
    }

    public File getVpnConfFile() {
        final File confFile = new File(VPN_CONFIG_PATH+device.getUuid()+".conf");
        if (!confFile.exists()) {
            // todo: try to regenerate algo users?
            log.error("confFile: file not found: "+abs(confFile));
            throw invalidEx("err.deviceVpnConf.confFileNotFound");
        }
        return confFile;
    }

    @GET @Path("/QR.png")
    @Produces(IMAGE_PNG)
    public Response qrCode(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(device.getAccount())) return forbidden();
        return send(new FileSendableResource(getQRfile()));
    }

    @GET @Path("/QR.png.base64")
    @Produces(TEXT_PLAIN)
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
