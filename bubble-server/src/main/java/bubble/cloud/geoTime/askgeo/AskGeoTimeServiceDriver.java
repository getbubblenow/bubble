package bubble.cloud.geoTime.askgeo;

import bubble.cloud.config.CloudApiConfig;
import bubble.cloud.geoTime.GeoTimeServiceDriver;
import bubble.cloud.geoTime.GeoTimeServiceDriverBase;
import bubble.cloud.geoTime.GeoTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import org.cobbzilla.util.http.HttpResponseBean;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class AskGeoTimeServiceDriver extends GeoTimeServiceDriverBase<CloudApiConfig> implements GeoTimeServiceDriver {

    public static final String ASKGEO_URL = "https://api.askgeo.com/v1"
            + "/{{params.account_id}}/{{apiKey}}/query.json"
            + "?databases=TimeZone&points={{lat}},{{lon}}";

    @Override public GeoTimeZone _getTimezone(String lat, String lon) {

        try {
            final HttpResponseBean response = fetch(ASKGEO_URL, config.latLonCtx(lat, lon, configuration.getEnvironment()));

            final JsonNode obj = json(response.getEntityString(), JsonNode.class);
            if (obj.get("code").intValue() != 0) {
                return die("getTimezone: error: " + response.getEntityString());
            }

            final JsonNode tz = obj.get("data").get(0).get("TimeZone");
            return new GeoTimeZone(tz.get("TimeZoneId").textValue(),
                    tz.get("WindowsStandardName").textValue(),
                    tz.get("CurrentOffsetMs").longValue());

        } catch (Exception e) {
            return die("getTimezone: "+e, e);
        }
    }

}
