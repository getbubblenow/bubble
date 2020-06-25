/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.dns;

import bubble.cloud.CloudServiceDriverBase;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import lombok.Getter;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_ALL_ADDRS;

public abstract class DnsDriverBase<T> extends CloudServiceDriverBase<T> implements DnsServiceDriver {

    private static final long DNS_LOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
    private static final long DNS_DEADLOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

    @Autowired protected BubbleDomainDAO domainDAO;
    @Autowired protected BubbleNetworkDAO networkDAO;
    @Autowired protected RedisService redis;

    @Getter(lazy=true) private final RedisService dnsLocks = redis.prefixNamespace(getClass().getSimpleName()+"_dns_lock");
    protected synchronized String lockDomain(String domain) {
        return getDnsLocks().lock(domain, DNS_LOCK_TIMEOUT, DNS_DEADLOCK_TIMEOUT);
    }

    protected synchronized void unlockDomain(String domain, String lock) {
        getDnsLocks().unlock(domain, lock);
    }

    protected BubbleDomain getDomain(String fqdn) { return domainDAO.findByFqdn(fqdn); }

    protected BubbleDomain getDomain(DnsRecordMatch matcher) {
        if (matcher == null) return null;

        String search;
        if (matcher.hasFqdn()) {
            search = matcher.getFqdn();
        } else if (matcher.hasSubdomain()) {
            search = matcher.getSubdomain();
        } else if (matcher.hasPattern()) {
            search = matcher.getPattern();
        } else {
            log.warn("getDomain("+matcher+"): matcher has no fqdn or subdomain, returning null");
            return null;
        }

        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) {
            // we are still in activation
            return new BubbleDomain().setName(search);
        }

        // todo: add accountUuid param, update DnsServiceDriver interface
        final String account = thisNode.getAccount();
        while (account != null && search.contains(".")) {
            final BubbleDomain found = domainDAO.findByAccountAndId(account, search);
            if (found != null) return found;
            search = search.substring(search.indexOf(".") + 1);
        }
        log.warn("getDomain("+matcher+"): fqdn/subdomain could not be resolved to a domain");
        return null;
    }

    @Override public Collection<DnsRecord> setNetwork(BubbleNetwork network) {
        final DnsServiceDriver dns = cloud.getDnsDriver(configuration);
        final Collection<DnsRecord> records = new ArrayList<>();
        if (dns.requireSubnetNS()) {
            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            for (String ns : dns.resolveNS(domain)) {
                records.add(dns.update((DnsRecord) new DnsRecord()
                        .setOption(DnsRecord.OPT_NS_NAME, ns)
                        .setType(DnsType.NS)
                        .setValue(IPv4_ALL_ADDRS)
                        .setFqdn(network.getNetworkDomain())));
            }
        }
        return records;
    }

    @Override public Collection<DnsRecord> setNode(BubbleNode node) {
        final DnsServiceDriver dns = cloud.getDnsDriver(configuration);
        final BubbleNetwork network = networkDAO.findByUuid(node.getNetwork());
        if (network == null) return die("setNode: network not found: "+node.getNetwork());;
        if (!node.hasIp4() && !node.hasIp6()) return die("setNode: both ip4 and ip6 are null: "+node.id());

        final Collection<DnsRecord> records = getNodeRecords(node, network);
        records.forEach(dns::update);
        return records;
    }

    @Override public Collection<DnsRecord> deleteNode(BubbleNode node) {
        final DnsServiceDriver dns = cloud.getDnsDriver(configuration);
        final BubbleNetwork network = networkDAO.findByUuid(node.getNetwork());
        if (network == null) return die("deleteNode: network not found: "+node.getNetwork());;

        final Collection<DnsRecord> records = getNodeRecords(node, network);
        records.forEach(dns::remove);
        return records;
    }

    protected Collection<DnsRecord> getNodeRecords(BubbleNode node, BubbleNetwork network) {
        final List<DnsRecord> records = new ArrayList<>();
        if (node.hasIp4()) {
            records.add((DnsRecord) new DnsRecord()
                    .setType(DnsType.A)
                    .setValue(node.getIp4())
                    .setFqdn(node.getFqdn()));
        }
        if (node.hasIp6()) {
            records.add((DnsRecord) new DnsRecord()
                    .setType(DnsType.AAAA)
                    .setValue(node.getIp6())
                    .setFqdn(node.getFqdn()));
        }
        records.add((DnsRecord) new DnsRecord()
                .setType(DnsType.CNAME)
                .setFqdn(network.getNetworkDomain())
                .setValue(node.getFqdn()));
        records.add((DnsRecord) new DnsRecord()
                .setType(DnsType.CNAME)
                .setFqdn(network.getCertCNAME())
                .setValue(network.getNetworkDomain()));
        return records;
    }

}
