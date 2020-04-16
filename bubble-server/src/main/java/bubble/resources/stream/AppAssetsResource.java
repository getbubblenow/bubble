/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.app.AppMessageDAO;
import bubble.model.app.AppMessage;
import bubble.model.app.BubbleApp;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.wizard.stream.DataUrlSendableResource;
import org.cobbzilla.wizard.stream.StreamStreamingOutput;
import org.cobbzilla.wizard.stream.UrlSendableResource;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.CLASSPATH_PREFIX;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.TEXT_PLAIN;
import static org.cobbzilla.util.http.HttpContentTypes.contentType;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsBytesOrDie;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.stream.DataUrlStreamingOutput.DATA_URL_PREFIX;
import static org.cobbzilla.wizard.stream.DataUrlStreamingOutput.dataUrlBytes;

@Slf4j
public class AppAssetsResource {

    private String locale;
    private BubbleApp app;

    public AppAssetsResource(String locale, BubbleApp app) {
        this.locale = locale;
        this.app = app;
    }

    @Autowired private AppMessageDAO appMessageDAO;

    @GET @Path("/{assetId}")
    @Produces(MediaType.WILDCARD)
    public Response findAsset(@Context Request req,
                              @Context ContainerRequest request,
                              @PathParam("assetId") String assetId,
                              @QueryParam("locale") String localeParam,
                              @QueryParam("raw") Boolean raw) {
        final boolean base64 = raw == null || !raw;
        final AppMessage message = findMessage(assetId, localeParam);
        if (message != null && message.hasMessage(assetId)) return sendAsset(message, assetId, base64);
        return notFound(assetId);
    }

    private AppMessage findMessage(String assetId, String localeParam) {
        AppMessage message;
        if (!empty(localeParam)) {
            message = appMessageDAO.findByAccountAndAppAndLocale(app.getAccount(), app.getUuid(), localeParam);
            if (message != null && message.hasMessage(assetId)) return message;
        }

        if (locale  != null) {
            message = appMessageDAO.findByAccountAndAppAndLocale(app.getAccount(), app.getUuid(), locale);
            if (message != null && message.hasMessage(assetId)) return message;
        }

        message = appMessageDAO.findByAccountAndAppAndDefaultLocale(app.getAccount(), app.getUuid());
        if (message != null && message.hasMessage(assetId)) return message;

        message = appMessageDAO.findByAccountAndAppAndHighestPriority(app.getAccount(), app.getUuid());
        if (message != null && message.hasMessage(assetId)) return message;

        return null;
    }

    private Response sendAsset(AppMessage message, String assetId, boolean base64) {
        final String val = message.getMessage(assetId);
        if (empty(val)) return notFound(assetId);

        if (val.startsWith(CLASSPATH_PREFIX)) {
            if (base64) {
                final byte[] b64bytes = dataUrlBytes(contentType(val), true, Base64.encodeBytes(loadResourceAsBytesOrDie(val.substring(CLASSPATH_PREFIX.length()))));
                return send(new StreamStreamingOutput(new ByteArrayInputStream(b64bytes)),
                        OK, null, assetId, TEXT_PLAIN, (long) b64bytes.length, false);
            } else {
                final InputStream in = loadResourceAsStream(val.substring(CLASSPATH_PREFIX.length()));
                return send(new StreamStreamingOutput(in),
                        OK, null, assetId, contentType(val), null, false);

            }
        }

        if (isHttpOrHttps(val)) return send(new UrlSendableResource(val, base64));

        if (val.startsWith(DATA_URL_PREFIX)) return send(new DataUrlSendableResource(assetId, val, base64));

        return invalid("err.assetId.invalid", "App asset with assetId '"+assetId+"' could not be retrieved", assetId);
    }

}
