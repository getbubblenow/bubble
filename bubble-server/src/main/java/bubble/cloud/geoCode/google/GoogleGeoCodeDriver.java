/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoCode.google;

import bubble.cloud.config.CloudApiConfig;
import bubble.cloud.geoCode.GeoCodeDriverBase;
import bubble.cloud.geoCode.GeoCodeResult;
import bubble.cloud.geoLocation.GeoLocation;
import com.fasterxml.jackson.databind.JsonNode;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.urlEncode;

public class GoogleGeoCodeDriver extends GeoCodeDriverBase<CloudApiConfig> {

    private static final String PARAM_API_KEY = "apiKey";

    @Override public GeoCodeResult _lookup(GeoLocation location) {
        final String apiKey = HandlebarsUtil.apply(getHandlebars(), credentials.getParam(PARAM_API_KEY), config.getCtx(configuration.getEnvironment()));
        final String url = "https://maps.googleapis.com/maps/api/geocode/json"
                + "?address=" + urlEncode(location.getAddress())
                + "&key=" + apiKey;
        HttpResponseBean response = null;
        try {
            response = HttpUtil.getResponse(url);
            final JsonNode json = json(response.getEntityString(), JsonNode.class);
            final JsonNode loc = json.get("results").get(0).get("geometry").get("location");
            return new GeoCodeResult()
                    .setLat(loc.get("lat").asText())
                    .setLon(loc.get("lng").asText());

        } catch (Exception e) {
            return response != null
                    ? die("lookup (status="+response.getStatus()+", entity="+response.getEntityString()+"): "+e)
                    : die("lookup: "+e, e);
        }
    }

}
