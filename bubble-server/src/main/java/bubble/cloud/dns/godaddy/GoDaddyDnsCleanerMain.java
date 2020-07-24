/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.dns.godaddy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.dns.DnsType;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.util.network.NetworkUtil;

import java.util.*;
import java.util.stream.Collectors;

import static bubble.cloud.dns.godaddy.GoDaddyDnsConfig.GODADDY_BASE_URI;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.PUT;
import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTP;
import static org.cobbzilla.util.http.HttpUtil.url2string;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.normalizeHost;

@Slf4j
public class GoDaddyDnsCleanerMain extends BaseMain<GoDaddyDnsCleanerOptions> {

    private static final DnsType[] DNS_TYPES = new DnsType[] { DnsType.A, DnsType.AAAA, DnsType.CNAME };

    public static void main(String[] args) { main(GoDaddyDnsCleanerMain.class, args); }

    @Override protected void run() throws Exception {

        final GoDaddyDnsCleanerOptions opts = getOptions();

        Set<String> bubbleHosts;
        try {
            bubbleHosts = new HashSet<>(FileUtil.toStringList(opts.getBubbleHostsFile()));
        } catch (Exception e) {
            err("Error reading bubbleHosts file: "+shortError(e));
            return;
        }
        if (empty(bubbleHosts)) {
            err("No bubble hosts found in file: "+abs(opts.getBubbleHostsFile()));
            return;
        }
        if (bubbleHosts.iterator().next().contains(",")) {
            bubbleHosts = bubbleHosts.stream()
                    .map(h -> h.split(",")[2])
                    .map(NetworkUtil::normalizeHost)
                    .collect(Collectors.toSet());
        }

        final String bootUrl = opts.getBootUrl();
        final HttpResponseBean bootJsonResponse = HttpUtil.getResponse(bootUrl);
        if (!bootJsonResponse.isOk()) {
            err("Error loading boot.json from "+bootUrl+": "+bootJsonResponse);
        }

        Set<String> sageSet = new TreeSet<>();
        try {
            final JsonNode bootJson = json(bootJsonResponse.getEntityString(), JsonNode.class);
            final ArrayNode sages = (ArrayNode) bootJson.get("sages");
            for (int i=0; i<sages.size(); i++) {
                sageSet.add(normalizeHost(sages.get(i).textValue()));
            }
        } catch (Exception e) {
            err("Error parsing boot.json ("+bootUrl+"): "+shortError(e));
            return;
        }

        final SortedSet<String> retain = new TreeSet<>();
        retain.addAll(bubbleHosts);
        retain.addAll(bubbleHosts.stream()
                .map(h -> {
                    final int firstDot = h.indexOf('.');
                    if (firstDot == -1 || firstDot == h.length()-1) throw new IllegalStateException("invalid bubble host: "+h);
                    return h.substring(firstDot+1);
                })
                .collect(Collectors.toSet()));
        retain.addAll(sageSet);
        retain.addAll(opts.getAdditionalSages());
        retain.addAll(opts.getRetainHosts());

        final GoDaddyDnsDriver dns = new GoDaddyDnsDriver();
        dns.setCredentials(opts.getCredentials());

        final Set<String> bubbleDomains = bubbleHosts.stream().map(URIUtil::hostToDomain).collect(Collectors.toSet());
        for (String domain : bubbleDomains) {
            final String url = GODADDY_BASE_URI + domain + "/records";
            try {
                for (DnsType type : DNS_TYPES) {
                    final Set<GoDaddyDnsRecord> removed = new HashSet<>();
                    final Set<GoDaddyDnsRecord> retainRecords = new HashSet<>();
                    final GoDaddyDnsRecord[] dnsRecords = dns._listGoDaddyDnsRecords(url+"/"+type);
                    for (GoDaddyDnsRecord rec : dnsRecords) {
                        final String host = rec.getName() + "." + domain;
                        if (retain.contains(host)) {
                            retainRecords.add(rec);
                        } else {
                            if (opts.hasHttpCheckTimeout()) {
                                try {
                                    url2string(SCHEME_HTTP + host + "/", opts.getHttpCheckTimeoutMillis());
                                    final String msg = "Host seems alive, not removing: " + host;
                                    log.warn(msg);
                                    out("WARN: " + msg);
                                    retainRecords.add(rec);
                                } catch (Exception e) {
                                    final String message = "Error connecting to " + host + ": " + shortError(e);
                                    log.debug(message);
                                    out(message);
                                    removed.add(rec);
                                }
                            } else {
                                removed.add(rec);
                            }
                        }
                    }

                    log.info("Removing "+type+" records:\n" + json(removed));
                    final HttpRequestBean domainUpdate = dns.auth(url+"/"+type)
                            .setMethod(PUT)
                            .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                            .setEntity(json(retainRecords));
                    final HttpResponseBean response = HttpUtil.getResponse(domainUpdate);
                    if (!response.isOk()) {
                        log.error("Error updating "+type+" records for domain: " + domain + ": " + response);
                    }
                }
            } catch (Exception e) {
                log.error("Error reading DNS records for domain: "+domain+": "+shortError(e));
            }
        }
    }

}
