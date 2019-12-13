package bubble.cloud.dns.godaddy;

import bubble.cloud.dns.DnsDriverBase;
import bubble.model.cloud.BubbleDomain;
import org.apache.http.HttpHeaders;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.retry;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.PATCH;
import static org.cobbzilla.util.json.JsonUtil.json;

public class GoDaddyDnsDriver extends DnsDriverBase<GoDaddyDnsConfig> {

    public static final int MAX_GODADDY_RETRIES = 5;

    @Override public boolean requireSubnetNS() { return false; }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) {
        // lookup SOA and NS records for domain, they must already exist
        final Collection<DnsRecord> soaRecords = readRecords(domain, urlForType(domain, "SOA"), matchSOA(domain));
        final List<DnsRecord> records = new ArrayList<>();
        if (soaRecords.isEmpty()) {
            log.warn("create: no SOA found for "+domain.getName());
        } else if (soaRecords.size() > 1) {
            log.warn("create: multiple SOA found for "+domain.getName());
        } else {
            records.add(soaRecords.iterator().next());
        }

        records.addAll(readRecords(domain, urlForType(domain, "NS"), matchNS(domain)));
        return records;
    }

    public String urlForType(BubbleDomain domain, final String type) {
        return config.getBaseUri()+domain.getName()+ "/records/" + type;
    }

    public DnsRecordMatch matchSOA(BubbleDomain domain) {
        return (DnsRecordMatch) new DnsRecordMatch().setType(DnsType.SOA).setFqdn(domain.getName());
    }

    public DnsRecordMatch matchNS(BubbleDomain domain) {
        return (DnsRecordMatch) new DnsRecordMatch().setType(DnsType.NS).setFqdn(domain.getName());
    }

    @Override public DnsRecord update(DnsRecord record) {
        String lock = null;
        BubbleDomain domain = null;
        try {
            domain = getDomain(record.getFqdn());

            if (domain == null) return die("update: domain not found for record: "+record.getFqdn());
            lock = lockDomain(domain.getUuid());

            final String name = dropDomainSuffix(domain, record.getFqdn());
            final String url = urlForType(domain, record.getType().name()) + "/" + name;
            final Collection<DnsRecord> found = readRecords(domain, url, null);
            if (record.getType() == DnsType.SOA || record.getType() == DnsType.NS) {
                // can't do this!
                log.warn("update: declining to call API to add SOA or NS record: " + record);
                return record;
            }
            if (found.isEmpty()) {
                final HttpRequestBean update = auth(config.getBaseUri() + domain.getName() + "/records")
                        .setMethod(PATCH)
                        .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setEntity(json(new GoDaddyDnsRecord[] {
                                new GoDaddyDnsRecord()
                                        .setName(name)
                                        .setType(record.getType())
                                        .setTtl(record.getTtl())
                                        .setData(record.getValue())
                        }));
                retry(() -> {
                    final HttpResponseBean response = HttpUtil.getResponse(update);
                    return response.isOk() ? response : die("update: " + response);
                }, MAX_GODADDY_RETRIES);
            }
            return record;

        } finally {
            if (lock != null) unlockDomain(domain.getUuid(), lock);
        }
    }

    @Override public DnsRecord remove(DnsRecord record) {
        // lookup record, does it exist? if so remove it.
        return record;  // removal of names is not supported.
        // if we kept track of ALL names in our db, we could do a "replace all" after removing from here....
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
                fqdn = dropDomainSuffix(domain, fqdn);
                url += "/" + fqdn;
            }
        }

        return readRecords(domain, url, matcher);
    }

    public String dropDomainSuffix(BubbleDomain domain, String fqdn) {
        if (fqdn.endsWith("." + domain.getName())) {
            fqdn = fqdn.substring(0, fqdn.length() - domain.getName().length() - 1);
        }
        return fqdn;
    }

    public Collection<DnsRecord> readRecords(BubbleDomain domain, String url, DnsRecordMatch matcher) {
        return retry(() -> {
            final HttpRequestBean request = auth(url);
            final HttpResponseBean response = HttpUtil.getResponse(request);
            if (!response.isOk()) {
                return die("readRecords: "+response);
            }

            final GoDaddyDnsRecord[] records = json(response.getEntityString(), GoDaddyDnsRecord[].class);
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
