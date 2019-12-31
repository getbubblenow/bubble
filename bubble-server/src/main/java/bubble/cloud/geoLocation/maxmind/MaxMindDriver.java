package bubble.cloud.geoLocation.maxmind;

import bubble.cloud.config.CloudApiUrlConfig;
import bubble.cloud.geoLocation.GeoLocateServiceDriverBase;
import bubble.cloud.geoLocation.GeoLocation;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.URIUtil.queryParams;

public class MaxMindDriver extends GeoLocateServiceDriverBase<CloudApiUrlConfig> {

    private DatabaseReader reader;

    @Override public String getExtension(String url) { return queryParams(url).get("suffix"); }

    @Override public void postSetup() {
        // grab latest DB
        final File database = initFile(config.getUrl(), config.getFile(), config.headersList());
        try {
            reader = new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            die("setConfig: "+e, e);
        }
    }

    @Override public GeoLocation _geolocate(String ip) {
        try {
            final InetAddress ipAddress = InetAddress.getByName(ip);
            final CityResponse response = reader.city(ipAddress);
            return new GeoLocation()
                    .setCountry(response.getCountry().getIsoCode())
                    .setRegion(response.getMostSpecificSubdivision().getName())
                    .setCity(response.getCity().getName());

        } catch (Exception e) {
            return die("geoLocation: "+e, e);
        }
    }

}
