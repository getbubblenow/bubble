package bubble.cloud.dns.godaddy;

import bubble.cloud.dns.DnsDriverBase;
import bubble.model.cloud.BubbleDomain;
import org.apache.http.HttpHeaders;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.retry;
import static org.cobbzilla.util.dns.DnsType.NS;
import static org.cobbzilla.util.dns.DnsType.SOA;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.PATCH;
import static org.cobbzilla.util.http.HttpMethods.PUT;
import static org.cobbzilla.util.json.JsonUtil.*;

public class GoDaddyDnsDriver extends DnsDriverBase<GoDaddyDnsConfig> {

    public static final int MAX_GODADDY_RETRIES = 5;

    @Override public boolean requireSubnetNS() { return false; }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) {
        // lookup SOA and NS records for domain, they must already exist
        final Collection<DnsRecord> soaRecords = readRecords(domain, urlForType(domain, SOA), domain.matchSOA());
        final List<DnsRecord> records = new ArrayList<>();
        if (soaRecords.isEmpty()) {
            log.warn("create: no SOA found for "+domain.getName());
        } else if (soaRecords.size() > 1) {
            log.warn("create: multiple SOA found for "+domain.getName());
        } else {
            records.add(soaRecords.iterator().next());
        }

        records.addAll(readRecords(domain, urlForType(domain, NS), domain.matchNS()));
        return records;
    }

    public String urlForDomain(BubbleDomain domain) { return config.getBaseUri() + domain.getName() + "/records/"; }

    public String urlForType(BubbleDomain domain, final DnsType type) {
        return urlForDomain(domain) + type;
    }

    public String urlForTypeAndName(BubbleDomain domain, final DnsType type, String name) {
        return urlForType(domain, type)+"/"+domain.dropDomainSuffix(name);
    }

    @Override public DnsRecord update(DnsRecord record) {
        String lock = null;
        BubbleDomain domain = null;
        try {
            domain = getDomain(record.getFqdn());

            if (domain == null) return die("update: domain not found for record: "+record.getFqdn());
            lock = lockDomain(domain.getUuid());

//            final String name = dropDomainSuffix(domain, record.getFqdn());
            final String name = domain.ensureDomainSuffix(record.getFqdn());
            final String url = urlForTypeAndName(domain, record.getType(), name);
            final Collection<DnsRecord> found = readRecords(domain, url, null);
            if (record.getType() == SOA || record.getType() == NS) {
                // can't do this!
                log.warn("update: declining to call API to add SOA or NS record: " + record);
                return record;
            }
            final String method;
            final String updateUrl;
            if (found.isEmpty()) {
                method = PATCH;
                updateUrl = urlForDomain(domain);
            } else if (found.size() == 1) {
                method = PUT;
                updateUrl = url;
            } else {
                return die("update("+json(record, COMPACT_MAPPER)+"): "+found.size()+" matching records found, cannot update");
            }
            final HttpRequestBean update = auth(updateUrl)
                    .setMethod(method)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setEntity(json(new GoDaddyDnsRecord[] {
                            new GoDaddyDnsRecord()
                                    .setName(domain.dropDomainSuffix(name))
                                    .setType(record.getType())
                                    .setTtl(record.getTtl())
                                    .setData(record.getValue())
                    }));
            retry(() -> {
                final HttpResponseBean response = HttpUtil.getResponse(update);
                return response.isOk() ? response : die("update: " + response);
            }, MAX_GODADDY_RETRIES);
            return record;

        } finally {
            if (lock != null) unlockDomain(domain.getUuid(), lock);
        }
    }

    @Override public DnsRecord remove(DnsRecord record) {
        String lock = null;
        final AtomicReference<BubbleDomain> domain = new AtomicReference<>();
        try {
            domain.set(getDomain(record.getFqdn()));

            if (domain.get() == null) return die("remove: domain not found for record: "+record.getFqdn());
            lock = lockDomain(domain.get().getUuid());

            final DnsRecordMatch nonMatcher = record.getNonMatcher();
            final String url = urlForDomain(domain.get());
            final GoDaddyDnsRecord[] gdRecords = listGoDaddyDnsRecords(url);
            final Collection<GoDaddyDnsRecord> retained = Arrays.stream(gdRecords)
                    .filter(r ->  nonMatcher.matches(r.toDnsRecord(domain.get())))
                    .collect(Collectors.toList());
            final HttpRequestBean remove = auth(url)
                    .setMethod(PUT)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setEntity(json(retained));
            retry(() -> {
                final HttpResponseBean response = HttpUtil.getResponse(remove);
                return response.isOk() ? response : die("remove: " + response);
            }, MAX_GODADDY_RETRIES);
            return record;

        } catch (IOException e) {
            return die("remove: "+e);

        } finally {
            if (lock != null && domain.get() != null) unlockDomain(domain.get().getUuid(), lock);
        }
    }

    @Override public Collection<DnsRecord> list(DnsRecordMatch matcher) {

        final BubbleDomain domain = getDomain(matcher);
        if (domain == null) return emptyList();

        // iterate over all records, return matches
        String url = config.getBaseUri()+domain.getName()+"/records";
        if (matcher != null) {
            if (matcher.hasType()) {
                url += "/" + matcher.getType().name();
            }
            if (matcher.hasFqdn()) {
                String fqdn = matcher.getFqdn();
                fqdn = domain.dropDomainSuffix(fqdn);
                url += "/" + fqdn;
            }
        }
        return readRecords(domain, url, matcher);
    }

    public Collection<DnsRecord> readRecords(BubbleDomain domain, String url, DnsRecordMatch matcher) {
        return retry(() -> {
            final GoDaddyDnsRecord[] records = listGoDaddyDnsRecords(url);
            final List<DnsRecord> out = new ArrayList<>();
            for (GoDaddyDnsRecord r : records) {
                final DnsRecord outRecord = r.toDnsRecord(domain);
                if (matcher == null || matcher.matches(outRecord)) {
                    out.add(outRecord);
                }
            }
            return out;
        }, MAX_GODADDY_RETRIES);
    }

    private final Map<String, GoDaddyDnsRecord[]> listCache = new ExpirationMap<>(SECONDS.toMillis(10));

    public GoDaddyDnsRecord[] listGoDaddyDnsRecords(String url) throws IOException {
        final HttpRequestBean request = auth(url);
        return listCache.computeIfAbsent(url, k -> {
            final HttpResponseBean response;
            try {
                response = HttpUtil.getResponse(request);
            } catch (Exception e) {
                log.error("listGoDaddyDnsRecords("+url+"): "+e);
                return GoDaddyDnsRecord.EMPTY_ARRAY;
            }
            if (!response.isOk()) throw new IllegalStateException("readRecords: "+response);
            return json(response.getEntityString(), GoDaddyDnsRecord[].class);
        });
    }

    public HttpRequestBean auth(String url) { return new HttpRequestBean(url).setHeader(HttpHeaders.AUTHORIZATION, authValue()); }

    public String authValue() {
        return "sso-key "
                + getCredentials().getParam("GODADDY_API_KEY")
                + ":" + getCredentials().getParam("GODADDY_API_SECRET");
    }

    @Override public Collection<DnsRecord> listNew(Long lastMod) {
        return list(); // not supported, we always return all matches
    }

}
