/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.model.device.DeviceStatus;
import bubble.model.device.FlexRouter;
import bubble.service.message.MessageService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.handlebars.HandlebarsUtil;

import java.util.HashMap;
import java.util.Map;

import static bubble.model.device.DeviceStatus.NO_DEVICE_STATUS;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;

@Accessors(chain=true) @ToString
public class FlexRouterInfo {

    @JsonIgnore @Getter private final FlexRouter router;
    @JsonIgnore @Getter private final DeviceStatus deviceStatus;
    @Getter @Setter private String auth;

    // set by missingFlexRouter method when there is no flex router but there should be one
    @Getter @Setter private String error_html;

    public FlexRouterInfo (FlexRouter router, DeviceStatus deviceStatus) {
        this.router = router;
        this.deviceStatus = deviceStatus;
    }

    @JsonIgnore public String getVpnIp () { return router.getIp(); }

    public int getPort () { return router == null ? -1 : router.getPort(); }
    public String getProxyUrl () { return router == null ? null  : router.proxyBaseUri(); }

    public boolean hasGeoLocation () { return hasDeviceStatus() && deviceStatus.getLocation() != null && deviceStatus.getLocation().hasLatLon(); }
    public boolean hasNoGeoLocation () { return !hasGeoLocation(); }

    public boolean hasDeviceStatus () { return deviceStatus != NO_DEVICE_STATUS && deviceStatus.hasIp(); }
    public boolean hasNoDeviceStatus () { return !hasDeviceStatus(); }

    public double distance(GeoLocation geoLocation) {
        return hasNoGeoLocation() ? Double.MAX_VALUE : deviceStatus.getLocation().distance(geoLocation);
    }

    public boolean hasIp () { return hasDeviceStatus() && deviceStatus.hasIp(); }
    public String ip () { return hasIp() ? deviceStatus.getIp() : null; }

    public FlexRouterInfo initAuth () { auth = json(router.pingObject(), COMPACT_MAPPER); return this; }

    @Override public int hashCode() { return getPort(); }

    @Override public boolean equals(Object obj) {
        return obj instanceof FlexRouterInfo && ((FlexRouterInfo) obj).getPort() == getPort();
    }

    public static final String CTX_ACCOUNT = "account";
    public static final String CTX_DEVICE = "device";
    public static final String CTX_MESSAGES = "messages";
    public static final String CTX_FLEX_FQDN = "flex_fqdn";
    public static final String CTX_DEVICE_TYPE_LABEL = "device_type_label";

    public static FlexRouterInfo missingFlexRouter(Account account,
                                                   Device device,
                                                   String fqdn,
                                                   MessageService messageService,
                                                   Handlebars handlebars) {
        final String locale = account.getLocale();
        final String template = messageService.loadPageTemplate(locale, "no_flex_router");
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_ACCOUNT, account);
        ctx.put(CTX_DEVICE, device);
        ctx.put(CTX_FLEX_FQDN, fqdn);

        final Map<String, String> messages = messageService.formatStandardMessages(locale);
        ctx.put(CTX_MESSAGES, messages);
        ctx.put(CTX_DEVICE_TYPE_LABEL, messages.get("device_type_"+device.getDeviceType().name()));

        final String html = HandlebarsUtil.apply(handlebars, template, ctx);
        return new FlexRouterInfo(null, null).setError_html(html);
    }

}
