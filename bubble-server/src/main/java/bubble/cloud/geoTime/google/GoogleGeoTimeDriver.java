/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.geoTime.google;

import bubble.cloud.config.CloudApiConfig;
import bubble.cloud.geoTime.GeoTimeServiceDriverBase;
import bubble.cloud.geoTime.GeoTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import org.cobbzilla.util.http.HttpResponseBean;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.json.JsonUtil.json;

public class GoogleGeoTimeDriver extends GeoTimeServiceDriverBase<CloudApiConfig> {

    private static final String PARAM_API_KEY = "apiKey";

    public static final String GOOGLE_URL = "https://maps.googleapis.com/maps/api/timezone/json"
            + "?location={{lat}},{{lon}}&timestamp={{since}}&key={{apiKey}}\n";

    @Override protected GeoTimeZone _getTimezone(String lat, String lon) {
        HttpResponseBean response = null;
        try {
            final Map env = configuration.getEnvironment();
            final Map<String, Object> ctx = config.latLonCtx(lat, lon, env);
            ctx.put("since", now()/1000);
            ctx.put("apiKey", credentials.getParam(PARAM_API_KEY));
            response = fetch(GOOGLE_URL, ctx);

            final JsonNode tz = json(response.getEntityString(), JsonNode.class);
            if (!tz.get("status").textValue().equals("OK")) {
                return die("getTimezone: status was not 'OK': "+response.getEntityString());
            }

            return new GeoTimeZone(tz.get("timeZoneId").textValue(),
                    tz.get("timeZoneName").textValue(),
                    tz.get("rawOffset").longValue() * 1000);

        } catch (Exception e) {
            return response != null
                    ? die("getTimezone: (status="+response.getStatus()+", entity="+response.getEntityString()+"): "+e, e)
                    : die("getTimezone: "+e, e);
        }
    }

}
