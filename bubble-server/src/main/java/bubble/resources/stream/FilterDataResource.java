/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.device.Device;
import bubble.rule.AppRuleDriver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class FilterDataResource {

    @Autowired private AppDataDAO dataDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private BubbleAppDAO appDAO;

    private final Account account;
    private final Device device;
    private final AppMatcher matcher;
    @Getter(lazy=true) private final AppRule rule = ruleDAO.findByUuid(matcher.getRule());
    @Getter(lazy=true) private final RuleDriver driver = driverDAO.findByUuid(getRule().getDriver());
    @Getter(lazy=true) private final BubbleApp app = appDAO.findByUuid(matcher.getApp());
    @Getter(lazy=true) private final AppRuleDriver ruleDriver = initAppRuleDriver();

    private AppRuleDriver initAppRuleDriver() {
        log.warn("initAppRuleDriver: initializing driver....");
        return getRule().initQuickDriver(getApp(), getDriver(), matcher, account, device);
    }

    public FilterDataResource (Account account, Device device, AppMatcher matcher) {
        this.account = account;
        this.device = device;
        this.matcher = matcher;
    }

    @GET @Path(EP_READ)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: read data",
            description="Read app-specific data. If `value` is specified, only return data that matches that value. Otherwise return all data. The `format` param determines what to return. Formats are: `key` (array of key names), `value` (array of values), `key_value` (map of key->value), or `full` (array of AppData objects). The default format is `key`",
            parameters=@Parameter(name="format", description="what to return. Formats are: `key` (default, array of key names), `value` (array of values), `key_value` (map of key->value), or `full` (array of AppData objects)"),
            responses=@ApiResponse(responseCode=SC_OK, description="type depends on `format`")
    )
    public Response readData(@Context Request req,
                             @Context ContainerRequest ctx,
                             @QueryParam("format") AppDataFormat format,
                             @QueryParam("value") String value) {

        final List<AppData> data = dataDAO.findEnabledByAccountAndAppAndSite(account.getUuid(), matcher.getApp(), matcher.getSite());

        if (log.isDebugEnabled()) log.debug("readData: found "+data.size()+" AppData records");

        if (format == null) format = AppDataFormat.key;

        final Stream<AppData> stream = empty(value)
                ? data.stream()
                : data.stream().filter(d -> value.equals(d.getData()));

        return ok(format.filter(stream));
    }

    @POST @Path(EP_WRITE)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: write data",
            description="Write app-specific data.",
            responses=@ApiResponse(responseCode=SC_OK, description="the AppData object that was written")
    )
    public Response writeData(@Context Request req,
                              @Context ContainerRequest ctx,
                              AppData data) {
        if (data == null || !data.hasKey()) throw invalidEx("err.key.required");
        return ok(writeData(data));
    }

    @GET @Path(EP_WRITE)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: write data then redirect",
            description="Write app-specific data. If `redirectLocation` param is set, return an HTTP redirect to that URL",
            parameters={
                    @Parameter(name=Q_DATA, description="the AppData object in JSON format", required=true),
                    @Parameter(name=Q_REDIRECT, description="the URL to redirect to")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="the AppData object that was written, or an HTTP redirect")
    )
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

    @GET @Path(EP_READ+"/rule/{id}")
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: read rule data",
            description="Read rule data. ",
            parameters=@Parameter(name="id", description="the ID of the data to read", required=true),
            responses={
                @ApiResponse(responseCode=SC_OK, description="some object that was read"),
                @ApiResponse(responseCode=SC_NOT_FOUND, description="no object found with the given id")
            }
    )
    public Response readRuleData(@Context Request req,
                                 @Context ContainerRequest ctx,
                                 @PathParam("id") String id) {
        final Object data = getRuleDriver().readData(id);
        return data == null ? notFound(id) : ok(data);
    }

}
