package bubble.cloud.geoLocation.whois;

import bubble.cloud.geoLocation.GeoLocateServiceDriverBase;
import bubble.cloud.geoLocation.GeoLocation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.system.CommandResult;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.cloud.geoLocation.GeoLocation.NULL_LOCATION;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.string.StringUtil.safeShellArg;
import static org.cobbzilla.util.string.ValidationRegexes.isHostname;
import static org.cobbzilla.util.system.CommandShell.exec;
import static org.cobbzilla.util.system.CommandShell.execScript;

@Slf4j
public class WhoisGeoLocationDriver extends GeoLocateServiceDriverBase<WhoisConfig> {

    // always flush once upon initialization
    private static final AtomicBoolean flushed = new AtomicBoolean(false);
    public boolean flush() {
        if (!flushed.get()) {
            synchronized (flushed) {
                if (!flushed.get()) {
                    getCache().flush();
                    flushed.set(true);
                }
            }
        }
        return flushed.get();
    }

    @Getter(lazy=true) private final String whois = initWhois();
    private String initWhois() {
        flush();
        final String cmd = execScript("which whois");
        return empty(cmd) ? die("initWhois: 'whois' command not found") : cmd.trim();
    }

    @Override public boolean test() {
        flush();
        return super.test();
    }

    @Override protected GeoLocation _geolocate(String ip) {
        try {
            final CommandLine commandLine = new CommandLine(getWhois());
            final WhoisConfig config = getConfig();
            if (config.hasHost()) {
                final String host = config.getHost();
                if (!isHostname(host)) return die("_geolocate: invalid host: "+host);
                commandLine.addArgument("-h").addArgument(safeShellArg(host));
            }
            if (config.hasPort()) commandLine.addArgument("-p").addArgument(""+config.getPort());
            commandLine.addArgument(ip);

            final CommandResult result = exec(commandLine);
            if (!result.isZeroExitStatus()) {
                log.error("_geolocate: 'whois' had non-zero exit status: " + result.getExitStatus() + "\nstderr=" + result.getStderr());
                return NULL_LOCATION;
            }

            final String[] lines = result.getStdout().split("\n");
            final Optional<String> countryLine = Arrays.stream(lines)
                    .filter(line -> line.trim().startsWith("Country:"))
                    .findFirst();
            if (countryLine.isEmpty()) {
                log.warn("_geolocate: No 'Country:' found in 'whois' output");
                return NULL_LOCATION;
            }
            final String[] parts = countryLine.get().split("\\s+");
            if (parts.length != 2) {
                log.warn("_geolocate: invalid Country: line: "+countryLine);
                return NULL_LOCATION;
            }
            final String country = parts[1];

            final GeoLocation found = getCountryMap().get(country.toUpperCase());
            return found == null ? NULL_LOCATION : found;

        } catch (Exception e) {
            log.error("_geolocate: error: "+shortError(e));
            return NULL_LOCATION;
        }
    }

}
