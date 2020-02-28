/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppDataFormat;
import bubble.model.app.AppMatcher;
import bubble.model.device.Device;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class FilterDataResource {

    private Account account;
    private Device device;
    private AppMatcher matcher;

    public FilterDataResource (Account account, Device device, AppMatcher matcher) {
        this.account = account;
        this.device = device;
        this.matcher = matcher;
    }

    @Autowired private AppDataDAO dataDAO;

    @GET @Path(EP_READ)
    @Produces(APPLICATION_JSON)
    public Response readData(@Context Request req,
                             @Context ContainerRequest ctx,
                             @QueryParam("format") AppDataFormat format) {

        final List<AppData> data = dataDAO.findEnabledByAccountAndAppAndSite(account.getUuid(), matcher.getApp(), matcher.getSite());

        if (log.isDebugEnabled()) log.debug("readData: found "+data.size()+" AppData records");

        if (format == null) format = AppDataFormat.key;
        switch (format) {
            case key:
                return ok(data.stream().map(AppData::getKey).collect(Collectors.toList()));
            case value:
                return ok(data.stream().map(AppData::getData).collect(Collectors.toList()));
            case key_value:
                return ok(data.stream().collect(Collectors.toMap(AppData::getKey, AppData::getData)));
            case full:
                return ok(data);
            default:
                throw notFoundEx(format.name());
        }
    }

    @POST @Path(EP_WRITE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response writeData(@Context Request req,
                              @Context ContainerRequest ctx,
                              AppData data) {
        if (data == null || !data.hasKey()) throw invalidEx("err.key.required");
        return ok(writeData(data));
    }

    @GET @Path(EP_WRITE)
    @Produces(APPLICATION_JSON)
    public Response writeData(@Context Request req,
                              @Context ContainerRequest ctx,
                              @QueryParam(Q_DATA) String dataJson,
                              @QueryParam(Q_REDIRECT) String redirectLocation) {
        if (empty(dataJson)) throw invalidEx("err.data.required");
        AppData data;
        try {
            data = json(dataJson, AppData.class);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("writeData: invalid data="+dataJson+": "+shortError(e));
            throw invalidEx("err.data.invalid");
        }
        if (!data.hasKey()) throw invalidEx("err.key.required");

        data = writeData(data);

        if (!empty(redirectLocation)) {
            if (redirectLocation.trim().equalsIgnoreCase(Boolean.FALSE.toString())) {
                return ok(data);
            } else {
                return redirect(redirectLocation);
            }
        } else {
            final String referer = req.getHeader("Referer");
            if (referer != null) return redirect(referer);
            return redirect(".");
        }
    }

    private AppData writeData(AppData data) {
        if (log.isDebugEnabled()) log.debug("writeData: received data=" + json(data, COMPACT_MAPPER));

        data.setAccount(account.getUuid());
        data.setDevice(device.getUuid());
        data.setApp(matcher.getApp());
        data.setSite(matcher.getSite());
        data.setMatcher(matcher.getUuid());

        if (log.isDebugEnabled()) log.debug("writeData: recording data=" + json(data, COMPACT_MAPPER));
        return dataDAO.set(data);
    }

}
