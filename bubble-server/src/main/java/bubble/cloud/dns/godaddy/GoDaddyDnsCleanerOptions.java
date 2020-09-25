/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.dns.godaddy;

import bubble.model.cloud.CloudCredentials;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.main.BaseMainOptions;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.network.NetworkUtil.toHostSet;

public class GoDaddyDnsCleanerOptions extends BaseMainOptions {

    public static final String VAR_GODADDY_API_KEY = "GODADDY_API_KEY";
    public static final String VAR_GODADDY_API_SECRET = "GODADDY_API_SECRET";
    public static final String DEFAULT_BOOT_URL = "https://raw.githubusercontent.com/getbubblenow/bubble-config/master/boot.json";

    public static final String USAGE_API_KEY_ENV_VAR = "Name of environment variable containing GoDaddy API Key. Default is "+VAR_GODADDY_API_KEY;
    public static final String OPT_API_KEY_ENV_VAR = "-k";
    public static final String LONGOPT_API_KEY_ENV_VAR = "--api-key";
    public static final int DEFAULT_HTTP_CHECK_TIMEOUT_SECONDS = 10;
    @Option(name=OPT_API_KEY_ENV_VAR, aliases=LONGOPT_API_KEY_ENV_VAR, usage=USAGE_API_KEY_ENV_VAR)
    @Getter @Setter private String apiKeyVar = VAR_GODADDY_API_KEY;

    public static final String USAGE_SECRET_KEY_ENV_VAR = "Name of environment variable containing GoDaddy Secret Key. Default is "+VAR_GODADDY_API_SECRET;
    public static final String OPT_SECRET_KEY_ENV_VAR = "-s";
    public static final String LONGOPT_SECRET_KEY_ENV_VAR = "--secret-key";
    @Option(name=OPT_SECRET_KEY_ENV_VAR, aliases=LONGOPT_SECRET_KEY_ENV_VAR, usage=USAGE_SECRET_KEY_ENV_VAR)
    @Getter @Setter private String secretKeyVar = VAR_GODADDY_API_SECRET;

    public static final String USAGE_LIVE_BUBBLES = "File containing live/running Bubbles, one hostname per line. DNS records for these will be retained";
    public static final String OPT_LIVE_BUBBLES = "-b";
    public static final String LONGOPT_LIVE_BUBBLES = "--bubbles-file";
    @Option(name=OPT_LIVE_BUBBLES, aliases=LONGOPT_LIVE_BUBBLES, usage=USAGE_LIVE_BUBBLES, required=true)
    @Getter @Setter private File bubbleHostsFile;

    public static final String USAGE_BOOT_CONFIG_URL = "Url or File to boot.json containing initial sages. Default is "+DEFAULT_BOOT_URL;
    public static final String OPT_BOOT_CONFIG_URL = "-B";
    public static final String LONGOPT_BOOT_CONFIG_URL = "--boot";
    @Option(name=OPT_BOOT_CONFIG_URL, aliases=LONGOPT_BOOT_CONFIG_URL, usage=USAGE_BOOT_CONFIG_URL)
    @Getter @Setter private String bootUrl = DEFAULT_BOOT_URL;

    public static final String USAGE_ADDITIONAL_SAGES = "File containing additional sage hosts, one per line";
    public static final String OPT_ADDITIONAL_SAGES = "-A";
    public static final String LONGOPT_ADDITIONAL_SAGES = "--additional-sages";
    @Option(name=OPT_ADDITIONAL_SAGES, aliases=LONGOPT_ADDITIONAL_SAGES, usage=USAGE_ADDITIONAL_SAGES)
    @Getter @Setter private File additionalSagesFile;
    public boolean hasAdditionalSagesFile () { return !empty(additionalSagesFile); }
    public Set<String> getAdditionalSages () throws IOException {
        return hasAdditionalSagesFile() ? toHostSet(additionalSagesFile) : Collections.emptySet();
    }

    public static final String USAGE_RETAIN_HOSTS = "File containing additional hosts to retain, one per line";
    public static final String OPT_RETAIN_HOSTS = "-R";
    public static final String LONGOPT_RETAIN_HOSTS = "--retain-hosts";
    @Option(name=OPT_RETAIN_HOSTS, aliases=LONGOPT_RETAIN_HOSTS, usage=USAGE_RETAIN_HOSTS)
    @Getter @Setter private File retainHostsFile;
    public boolean hasRetainHostsFile () { return !empty(retainHostsFile); }
    public Set<String> getRetainHosts () throws IOException {
        return hasRetainHostsFile() ? toHostSet(retainHostsFile) : Collections.emptySet();
    }

    public static final String USAGE_RETAIN_MATCHING_CNAMES = "File containing values to match in CNAME records, one per line. CNAME records containing one of these values will be retained.";
    public static final String OPT_RETAIN_MATCHING_CNAMES = "-C";
    public static final String LONGOPT_RETAIN_MATCHING_CNAMES = "--retain-matching-cnames";
    @Option(name=OPT_RETAIN_MATCHING_CNAMES, aliases=LONGOPT_RETAIN_MATCHING_CNAMES, usage=USAGE_RETAIN_MATCHING_CNAMES)
    @Getter @Setter private File retainMatchingCNAMEsFile;
    public boolean hasRetainMatchingCNAMEsFile () { return !empty(retainMatchingCNAMEsFile); }
    public Set<String> getRetainMatchingCNAMEValues () throws IOException {
        return hasRetainMatchingCNAMEsFile() ? toSet(retainMatchingCNAMEsFile) : Collections.emptySet();
    }

    private Set<String> toSet(File f) {
        try {
            return FileUtil.toStringList(f).stream()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return die("toSet: "+e);
        }
    }

    public static final String USAGE_HTTP_CHECK = "Timeout for HTTP check in seconds. Use zero to disable check. Default is "+DEFAULT_HTTP_CHECK_TIMEOUT_SECONDS+" seconds";
    public static final String OPT_HTTP_CHECK = "-H";
    public static final String LONGOPT_HTTP_CHECK = "--http-check-timeout";
    @Option(name=OPT_HTTP_CHECK, aliases=LONGOPT_HTTP_CHECK, usage=USAGE_HTTP_CHECK)
    @Getter @Setter private long httpCheckTimeout = DEFAULT_HTTP_CHECK_TIMEOUT_SECONDS;
    public boolean hasHttpCheckTimeout () { return getHttpCheckTimeout() > 0; }
    public long getHttpCheckTimeoutMillis () { return SECONDS.toMillis(getHttpCheckTimeout()); }

    public CloudCredentials getCredentials() {
        final String apiKey = System.getenv(getApiKeyVar());
        if (empty(apiKey)) throw new IllegalArgumentException("Env var not found or empty: "+getApiKeyVar());

        final String secretKey = System.getenv(getSecretKeyVar());
        if (empty(secretKey)) throw new IllegalArgumentException("Env var not found or empty: "+getApiKeyVar());

        return new CloudCredentials(new NameAndValue[] {
                new NameAndValue(VAR_GODADDY_API_KEY, apiKey),
                new NameAndValue(VAR_GODADDY_API_SECRET, secretKey),
        });
    }
}
