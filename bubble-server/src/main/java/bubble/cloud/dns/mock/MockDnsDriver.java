/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.dns.mock;

import bubble.cloud.config.CloudApiConfig;
import bubble.cloud.dns.DnsDriverBase;
import bubble.model.cloud.BubbleDomain;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MockDnsDriver extends DnsDriverBase<CloudApiConfig> {

    private Map<String, DnsRecord> records = new ConcurrentHashMap<>();

    private String recordKey(DnsRecord record) { return record.getType()+":"+record.getFqdn(); }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) { return Collections.emptyList(); }

    @Override public DnsRecord update(DnsRecord record) {
        records.put(recordKey(record), record);
        return record;
    }

    @Override public DnsRecord remove(DnsRecord record) {
        records.remove(recordKey(record));
        return record;
    }

    @Override public Collection<DnsRecord> list(DnsRecordMatch matcher) {
        return records.values().stream().filter(matcher::matches).collect(Collectors.toList());
    }

}
