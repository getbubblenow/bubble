/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.dns;

import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.json.JsonUtil.json;

@NoArgsConstructor @Accessors(chain=true)
public class DnsDriverNotification extends SynchronousNotification {

    @Getter @Setter private BubbleDomain domain;
    public boolean hasDomain () { return domain != null; }

    @Getter @Setter private BubbleNetwork network;
    public boolean hasNetwork () { return network != null; }

    @Getter @Setter private BubbleNode node;
    public boolean hasNode () { return node != null; }

    @Getter @Setter private DnsRecord record;
    public boolean hasRecord () { return record != null; }

    @Getter @Setter private DnsRecordMatch matcher;

    @Getter @Setter private String dnsService;

    public DnsDriverNotification(BubbleNode node) { this.node = node; }

    public DnsDriverNotification(BubbleDomain domain) { this.domain = domain; }

    public DnsDriverNotification(BubbleNetwork network) { this.network = network; }

    public DnsDriverNotification(DnsRecord record) { this.record = record; }

    public DnsDriverNotification(DnsRecordMatch matcher) { this.matcher = matcher; }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(hasDomain() ? domain.getUuid() : null, hasNetwork() ? network.getUuid() : null, hasNode() ? node.getUuid() : null,
            hasRecord() ? getRecord().dnsUniq(): null, matcher != null ? json(matcher) : null, dnsService);
}
