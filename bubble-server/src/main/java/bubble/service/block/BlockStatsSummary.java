/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.block;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class BlockStatsSummary {

    private final Map<String, AtomicInteger> blocks = new HashMap<>();

    public void addBlock(BlockStatRecord rec) {
        final AtomicInteger ct = blocks.computeIfAbsent(rec.getFqdn(), k -> new AtomicInteger(0));
        ct.incrementAndGet();
    }

    public List<FqdnBlockCount> getBlocks () {
        final List<FqdnBlockCount> fqdnBlockCounts = new ArrayList<>();
        for (Map.Entry<String, AtomicInteger> entry : blocks.entrySet()) {
            final int count = entry.getValue().get();
            if (count > 0) fqdnBlockCounts.add(new FqdnBlockCount(entry.getKey(), count));
        }
        log.info("getBlocks returning counts="+json(fqdnBlockCounts)+" for blocks="+json(blocks));
        Collections.sort(fqdnBlockCounts);
        return fqdnBlockCounts;
    }

    public int getTotal () {
        int total = 0;
        for (Map.Entry<String, AtomicInteger> entry : blocks.entrySet()) {
            final String fqdn = entry.getKey();
            final int ct = entry.getValue().get();
            log.debug("getTotal: adding "+ct+" from fqdn="+fqdn);
            total += ct;
        }
        return total;
    }

    @Override public String toString () { return "BlockStatsSummary{total="+getTotal()+"}"; }

    @AllArgsConstructor @EqualsAndHashCode(of={"fqdn"})
    public static class FqdnBlockCount implements Comparable<FqdnBlockCount> {
        @Getter private final String fqdn;
        @Getter private final int count;
        @Override public int compareTo(FqdnBlockCount o) { return Integer.compare(o.count, count); }
    }

}
