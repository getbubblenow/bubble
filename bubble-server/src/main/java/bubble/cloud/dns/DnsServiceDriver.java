/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.dns;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordBase;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;
import org.cobbzilla.util.string.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.dns.DnsRecord.OPT_NS_NAME;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_ALL_ADDRS;
import static org.cobbzilla.util.network.NetworkUtil.ipEquals;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.util.system.Sleep.sleep;

public interface DnsServiceDriver extends CloudServiceDriver {

    Logger log = LoggerFactory.getLogger(DnsServiceDriver.class);

    String[] EXTERNAL_DNS_HOSTS = { "1.1.1.1", "8.8.8.8" };
    long RESOLVE_SLEEP_INTERVAL = SECONDS.toMillis(5);

    @Override default CloudServiceType getType() { return CloudServiceType.dns; }

    Collection<DnsRecord> create(BubbleDomain domain);

    Collection<DnsRecord> setNetwork(BubbleNetwork network);
    Collection<DnsRecord> setNode(BubbleNode node);
    Collection<DnsRecord> deleteNode(BubbleNode node);

    DnsRecord update(DnsRecord record);
    DnsRecord remove(DnsRecord record);

    Collection<DnsRecord> list(DnsRecordMatch matcher);

    default Collection<DnsRecord> list() { return list(null); }

    @Override default boolean test(Object arg) {
        return arg == null ? test() : !empty(list((DnsRecordMatch) new DnsRecordMatch().setPattern(arg.toString()).setType(DnsType.NS)));
    }
    @Override default boolean test() { return true; }

    default Collection<DnsRecord> listNew(Long lastMod) { return list(); }

    static Collection<DnsRecord> dig(String host, DnsType type, String name) { return dig(host, 53, type, name); }

    static Collection<DnsRecord> dig(String host, int port, DnsType type, String name) {
        final String output = execScript("dig +tcp +short +nocomment "
                + "@" + host
                + " -p " + port
                + " -t " + type.name()
                + " -q " + name);
        log.info("dig @"+host+" "+name+": output: "+output.trim());
        final List<DnsRecord> records = new ArrayList<>();
        for (String line : output.split("[\n]+")) {
            try {
                final DnsRecord rec = recordFromLine(type, name, line);
                if (rec != null) records.add(rec);
            } catch (Exception e) {
                log.error("dig: error adding record (line="+line+"): "+e, e);
            }
        }
        return records;
    }

    default Collection<DnsRecord> dig(BubbleDomain domain, DnsType type, String name) {
        return dig(resolveNS(domain).iterator().next(), 53, type, name);
    }

    static DnsRecord recordFromLine(DnsType type, String name, String line) {
        line = line.trim();
        if (line.startsWith("\"") && line.endsWith("\"")) line = line.substring(1, line.length()-1).trim();
        if (line.startsWith("/")) line = line.substring(1);
        if (line.endsWith(".")) line = line.substring(0, line.length()-1);
        line = line.trim();
        if (line.length() == 0 || line.startsWith(";")) return null;
        switch (type) {
            case NS:
                return (DnsRecord) new DnsRecord()
                        .setOption(OPT_NS_NAME, line.trim())
                        .setType(type)
                        .setFqdn(name)
                        .setValue(IPv4_ALL_ADDRS);
            case SOA:
                final String[] parts = line.split("\\s+");
                for (int i=0; i<parts.length; i++) {
                    if (parts[i].endsWith(".")) parts[i] = parts[i].substring(0, parts[i].length() - 1);
                }
                return (DnsRecord) new DnsRecord()
                        .setOption(DnsRecord.OPT_SOA_MNAME, parts[0])
                        .setOption(DnsRecord.OPT_SOA_RNAME, parts[1])
                        .setOption(DnsRecord.OPT_SOA_SERIAL, parts.length <= 2 ? null : parts[2])
                        .setOption(DnsRecord.OPT_SOA_REFRESH, parts.length <= 3 ? null : parts[3])
                        .setOption(DnsRecord.OPT_SOA_RETRY, parts.length <= 4 ? null : parts[4])
                        .setOption(DnsRecord.OPT_SOA_EXPIRE, parts.length <= 5 ? null : parts[5])
                        .setType(type)
                        .setFqdn(name)
                        .setValue(IPv4_ALL_ADDRS);

            case A: case AAAA: case CNAME: case TXT: default:
                return (DnsRecord) new DnsRecord()
                        .setType(type)
                        .setFqdn(name)
                        .setValue(line.trim());
        }
    }

    @SuppressWarnings("Duplicates")
    default Set<String> resolve(BubbleDomain domain, DnsType type, String fqdn, String expected, long expire) {

        Set<String> check = null;

        // first try our own name servers
        final Set<String> domainNameservers = resolveNS(domain);
        for (String ns : domainNameservers) {
            final Set<String> resolved = ensureResolved(ns, type, fqdn, expected, expire);
            if (resolved == null) return die("resolve: error resolving "+fqdn+" against domain DNS server: "+ns);
            if (check == null) {
                check = resolved;

            } else if (!check.containsAll(resolved) && !resolved.containsAll(check)) {
                return die("resolve: "+ns+" resolved differently ("+StringUtil.toString(resolved)+") than before ("+StringUtil.toString(check)+")");
            }
        }

        // now ensure it is visible externally
        for (String ns : EXTERNAL_DNS_HOSTS) {
            final Set<String> resolved = ensureResolved(ns, type, fqdn, expected, expire);
            if (resolved == null) return die("resolve: error resolving "+fqdn+" against domain DNS server: "+ns);
            if (check == null) {
                check = resolved;

            } else if (!check.containsAll(resolved) && !resolved.containsAll(check)) {
                return die("resolve: "+ns+" resolved differently ("+StringUtil.toString(resolved)+") than before ("+StringUtil.toString(check)+")");
            }
        }

        return check;
    }

    default Set<String> ensureResolved(String ns, DnsType type, String fqdn, String expected, long expire) {
        while (now() < expire) {
            try {
                final Set<String> resolved = dig(ns, type, fqdn).stream().map(DnsRecordBase::getValue).collect(Collectors.toSet());
                if (resolved.size() == 1 && ipEquals(resolved.iterator().next(), expected)) {
                    return resolved;
                }
            } catch (Exception e) {
                log.warn("ensureResolved: "+e);
            }
            sleep(RESOLVE_SLEEP_INTERVAL, "ensureResolved: awaiting DNS resolution of "+fqdn+" -> "+expected);
        }
        return null;
    }

    default Set<String> resolveNS(BubbleDomain domain) {
        // all external providers must agree
        Set<String> check = null;
        for (String dnsServer : EXTERNAL_DNS_HOSTS) {
            final Set<String> nsNames = dig(dnsServer, DnsType.NS, domain.getName())
                    .stream()
                    .map(r -> r.getOption(OPT_NS_NAME))
                    .collect(Collectors.toSet());
            if (check == null) {
                check = nsNames;

            } else if (check.size() < 2) {
                return die("resolveNS: "+dnsServer+" expected 2+ nameservers, found only: "+StringUtil.toString(check));

            } else if (!check.containsAll(nsNames) || !nsNames.containsAll(check)) {
                return die("resolveNS: "+dnsServer+" resolved a different set of nameservers ("+StringUtil.toString(nsNames)+") than we saw before "+StringUtil.toString(check));
            }
        }
        return check;
    }

    default boolean ensureResolvable(BubbleDomain domain, BubbleNode node, long timeout) {
        final long start = now();
        final long expire = start + timeout;
        boolean ok = false;
        for (DnsType type : DnsType.A_TYPES) {
            final String addr = type == DnsType.A ? node.getIp4() : node.getIp6();
            Set<String> resolved = null;
            while (now() - start < timeout) {
                resolved = resolve(domain, type, node.getFqdn(), addr, expire);
                if (!resolved.isEmpty() && resolved.size() == 1 && ipEquals(resolved.iterator().next(), addr)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return die("ensureResolvable: error resolving " + type + " record for node " + node.getFqdn() + ", expected " + addr + ", found: " + StringUtil.toString(resolved));
            }
        }
        return true;
    }

    default boolean requireSubnetNS() { return false; }
}
