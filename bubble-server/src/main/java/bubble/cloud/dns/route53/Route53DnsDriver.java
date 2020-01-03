package bubble.cloud.dns.route53;

import bubble.cloud.dns.DnsDriverBase;
import bubble.cloud.shared.aws.BubbleAwsCredentialsProvider;
import bubble.model.cloud.BubbleDomain;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;
import lombok.Getter;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.dns.DnsRecordBase.dropTailingDot;
import static org.cobbzilla.util.dns.DnsType.*;

public class Route53DnsDriver extends DnsDriverBase<Route53DnsConfig> {

    @Getter(lazy=true) private final AWSCredentialsProvider route53credentials = new BubbleAwsCredentialsProvider(cloud, getCredentials());

    @Getter(lazy=true) private final AmazonRoute53 route53client = initRoute53Client();
    private AmazonRoute53 initRoute53Client() {
        final Regions region;
        final String regionName = config.getRegion().name();
        try {
            region = Regions.valueOf(regionName);
        } catch (Exception e) {
            return die("initRoute53Client: invalid region: "+ regionName);
        }
        return AmazonRoute53ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(getRoute53credentials())
                .build();
    }

    @Getter(lazy=true) private final Map<String, HostedZone> cachedZoneLookups = new ExpirationMap<>();
    private HostedZone getHostedZone(AmazonRoute53 client, BubbleDomain domain) {
        return getCachedZoneLookups().computeIfAbsent(domain.getName(), key -> {
            try {
                final ListHostedZonesResult zones = client.listHostedZones(new ListHostedZonesRequest().withMaxItems("100"));
                for (HostedZone z : zones.getHostedZones()) {
                    if (z.getName().equalsIgnoreCase(key + ".")) return z;
                }
                return die("HostedZone with name '"+key+".' not found");
            } catch (Exception e) {
                return die("getHostedZone: "+e);
            }
        });
    }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) {

        final AmazonRoute53 client = getRoute53client();
        final HostedZone hostedZone = getHostedZone(client, domain);

        final ListResourceRecordSetsResult soaRecords = client.listResourceRecordSets(new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withStartRecordName(domain.getName())
                .withMaxItems("100")
                .withStartRecordType(SOA.name()));
        if (soaRecords.isTruncated()) {
            // should never happen
            return die("create: SOA records are too numerous ("+soaRecords.getResourceRecordSets().size()+"), result was truncated");
        }

        // lookup SOA and NS records for domain, they must already exist
        final List<DnsRecord> records = toDnsRecords(soaRecords.getResourceRecordSets());
        if (records.isEmpty()) {
            log.warn("create: no SOA found for "+domain.getName());
        } else if (records.size() > 1) {
            log.warn("create: multiple SOA found for "+domain.getName());
        }

        final ListResourceRecordSetsResult nsRecords = client.listResourceRecordSets(new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withStartRecordName(domain.getName())
                .withMaxItems("100")
                .withStartRecordType(NS.name()));
        if (nsRecords.isTruncated()) {
            // should never happen
            return die("create: NS records are too numerous ("+nsRecords.getResourceRecordSets().size()+"), result was truncated");
        }

        records.addAll(toDnsRecords(nsRecords.getResourceRecordSets()));
        return records;
    }

    private List<DnsRecord> toDnsRecords(List<ResourceRecordSet> recordSets) {
        final List<DnsRecord> records = new ArrayList<>();
        for (ResourceRecordSet rrs : recordSets) {
            records.addAll(toDnsRecords(rrs));
        }
        return records;
    }

    private Collection<DnsRecord> toDnsRecords(ResourceRecordSet rrs) {
        final List<DnsRecord> records = new ArrayList<>();
        for (ResourceRecord r : rrs.getResourceRecords()) {
            records.add(toDnsRecord(rrs, r));
        }
        return records;
    }

    private DnsRecord toDnsRecord(ResourceRecordSet rrs, ResourceRecord r) {
        final DnsRecord rec = (DnsRecord) new DnsRecord()
                .setType(DnsType.fromString(rrs.getType()))
                .setFqdn(dropTailingDot(rrs.getName()));
        final Long ttl = rrs.getTTL();
        if (ttl != null) rec.setTtl(ttl.intValue());
        rec.setValue(parseValue(rec, r.getValue()));
        return rec;
    }

    private String parseValue(DnsRecord rec, String value) {
        switch (rec.getType()) {
            case TXT:
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    return value.substring(1, value.length()-1);
                }
                return value;
            default:
                return dropTailingDot(value);
        }
    }

    private ResourceRecordSet toRoute53RecordSet(DnsRecord record) {
        return new ResourceRecordSet().withName(record.getFqdn()+".")
                .withTTL((long) record.getTtl())
                .withType(record.getType().name())
                .withResourceRecords(new ResourceRecord(record.getType() == TXT ? "\""+record.getValue()+"\"" : record.getValue()));
    }

    @Override public DnsRecord update(DnsRecord record) {
        final AmazonRoute53 client = getRoute53client();
        final BubbleDomain domain = getDomain(record.getFqdn());
        final HostedZone hostedZone = getHostedZone(client, domain);
        final ChangeResourceRecordSetsResult changeResult = client.changeResourceRecordSets(new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withChangeBatch(new ChangeBatch().withChanges(new Change()
                        .withAction(ChangeAction.UPSERT)
                        .withResourceRecordSet(toRoute53RecordSet(record)))));
        switch (changeResult.getChangeInfo().getStatus()) {
            case "PENDING": case "INSYNC": return record;
            default: return die("update: unrecognized changeResult.changeInfo.status: "+changeResult.getChangeInfo().getStatus());
        }
    }

    @Override public DnsRecord remove(DnsRecord record) {
        final AmazonRoute53 client = getRoute53client();
        final BubbleDomain domain = getDomain(record.getFqdn());
        final HostedZone hostedZone = getHostedZone(client, domain);

        final ListResourceRecordSetsRequest listRequest = new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withStartRecordName(record.getFqdn())
                .withStartRecordType(record.getType().name())
                .withMaxItems("100");
        ListResourceRecordSetsResult recordSets = client.listResourceRecordSets(listRequest);

        final DnsRecordMatch matcher = record.getMatcher();
        final List<ResourceRecordSet> toDelete = rawMatches(recordSets.getResourceRecordSets(), matcher);

        final ChangeResourceRecordSetsRequest changeRequest = new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withChangeBatch(new ChangeBatch().withChanges(toDelete.stream().map(rrs -> new Change()
                        .withAction(ChangeAction.DELETE)
                        .withResourceRecordSet(rrs)
                ).collect(Collectors.toList())));

        final ChangeResourceRecordSetsResult changeResult = client.changeResourceRecordSets(changeRequest);
        switch (changeResult.getChangeInfo().getStatus()) {
            case "PENDING": case "INSYNC": return record;
            default: return die("update: changeResult.changeInfo.status was "+changeResult.getChangeInfo().getStatus());
        }
    }

    @Override public Collection<DnsRecord> list(DnsRecordMatch matcher) {
        final BubbleDomain domain = getDomain(matcher.getFqdn());
        if (domain == null) return emptyList();

        final AmazonRoute53 client = getRoute53client();
        final HostedZone hostedZone = getHostedZone(client, domain);
        final ListResourceRecordSetsRequest listRequest = new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withStartRecordName(matcher.hasFqdn() ? matcher.getFqdn() : domain.getName())
                .withStartRecordType(matcher.hasType() ? matcher.getType().name() : null)
                .withMaxItems("100");
        ListResourceRecordSetsResult recordSets = client.listResourceRecordSets(listRequest);

        final List<DnsRecord> matched = matches(recordSets.getResourceRecordSets(), matcher);

        while (recordSets.isTruncated()) {
            recordSets = client.listResourceRecordSets(new ListResourceRecordSetsRequest()
                    .withHostedZoneId(hostedZone.getId())
                    .withStartRecordIdentifier(recordSets.getNextRecordIdentifier())
                    .withStartRecordType(recordSets.getNextRecordType())
                    .withStartRecordName(recordSets.getNextRecordName()));

            matched.addAll(matches(recordSets.getResourceRecordSets(), matcher));
        }

        return matched;
    }

    private List<DnsRecord> matches(List<ResourceRecordSet> recordSets, DnsRecordMatch matcher) {
        final List<DnsRecord> records = new ArrayList<>();
        for (ResourceRecordSet rrs : recordSets) {
            final List<DnsRecord> matched = matches(rrs, matcher);
            records.addAll(matched);
        }
        return records;
    }

    private List<DnsRecord> matches(ResourceRecordSet rrs, DnsRecordMatch matcher) {
        return toDnsRecords(rrs).stream().filter(matcher::matches).collect(Collectors.toList());
    }

    private List<ResourceRecordSet> rawMatches(List<ResourceRecordSet> recordSets, DnsRecordMatch matcher) {
        final List<ResourceRecordSet> matches = new ArrayList<>();
        for (ResourceRecordSet rrs : recordSets) {
            final List<ResourceRecord> matchedRecords = rawMatches(rrs, matcher);
            if (!matchedRecords.isEmpty()) matches.add(rrs.withResourceRecords(matchedRecords));
        }
        return matches;
    }
    private List<ResourceRecord> rawMatches(ResourceRecordSet rrs, DnsRecordMatch matcher) {
        final List<ResourceRecord> matches = new ArrayList<>();
        for (ResourceRecord r : rrs.getResourceRecords()) {
            if (matcher.matches(toDnsRecord(rrs, r))) {
                matches.add(r);
            }
        }
        return matches;
    }

}