/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.filter;

import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import bubble.service.block.BlockStatRecord;
import bubble.service.block.BlockStatsService;
import bubble.service.block.BlockStatsSummary;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.URIUtil;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.junit.Assert.assertEquals;

@Slf4j
public class BlockSummaryTest {

    public static final BlockStatsService STATS_SERVICE = new BlockStatsService();

    public static final String BASE_FQDN = "example.com";
    public static final String BASE_URL = "https://" + BASE_FQDN + "/";
    public static final String INITIAL_URL = BASE_URL + "page.html";

    public static final String SCRIPT_FQDN = "js.example.com";
    public static final String SCRIPT_BASE_URL = "https://" + SCRIPT_FQDN + "/";
    public static final String SCRIPT_URL = SCRIPT_BASE_URL + "script.js";
    public static final String SCRIPT2_URL = SCRIPT_BASE_URL + "script2.js";

    public static final String TRACKER_FQDN = "tracker.example.com";
    public static final String TRACKER_BASE_URL = "https://" + TRACKER_FQDN + "/";
    public static final String TRACKER_URL = TRACKER_BASE_URL + "track.json";

    public static final String[][] SIMPLE_TEST = {
        //   url                       referer                  decision
            {INITIAL_URL,              null,                    FilterMatchDecision.match.name()},
            {SCRIPT_URL,               INITIAL_URL,  FilterMatchDecision.abort_not_found.name()},
            {SCRIPT2_URL,              INITIAL_URL,  FilterMatchDecision.abort_not_found.name()}
    };

    @Test public void testSimpleSummary () throws Exception {
        final BlockStatsSummary summary = runTest(SIMPLE_TEST);
        assertEquals("expected total == 2", 2, summary.getTotal());
        assertEquals("expected 1 blocked fqdn", 1, summary.getBlocks().size());
        assertEquals("expected 2 blocks for fqdn", 2, findBlock(summary, SCRIPT_FQDN).getCount());
    }

    public static final String[][] BARE_REFERER_TEST = {
        //   url                       referer                  decision
            {INITIAL_URL,              null,                    FilterMatchDecision.match.name()},
            {SCRIPT_URL,               BASE_URL,                FilterMatchDecision.abort_not_found.name()},
            {SCRIPT2_URL,              BASE_URL,                FilterMatchDecision.abort_not_found.name()}
    };

    @Test public void testBareReferer () throws Exception {
        final BlockStatsSummary summary = runTest(BARE_REFERER_TEST);
        assertEquals("expected total == 2", 2, summary.getTotal());
        assertEquals("expected 1 blocked fqdn", 1, summary.getBlocks().size());
        assertEquals("expected 2 blocks for fqdn", 2, findBlock(summary, SCRIPT_FQDN).getCount());
    }

    public static final String[][] NESTED_BLOCK_TEST = {
        //   url                      referer                  decision
            {INITIAL_URL,             null,                    FilterMatchDecision.match.name()},
            {SCRIPT_URL,              BASE_URL,                FilterMatchDecision.no_match.name()},
            {TRACKER_URL,             SCRIPT_URL,              FilterMatchDecision.abort_not_found.name()}
    };

    @Test public void testNestedBlock () throws Exception {
        final BlockStatsSummary summary = runTest(NESTED_BLOCK_TEST);
        assertEquals("expected total == 1", 1, summary.getTotal());
        assertEquals("expected 1 blocked fqdn", 1, summary.getBlocks().size());
        assertEquals("expected 1 blocks for fqdn", 1, findBlock(summary, TRACKER_FQDN).getCount());
    }

    public static final String[][] NESTED_BLOCK_WITH_REPEAT_TEST = {
        //   url                      referer                  decision
            {INITIAL_URL,             null,                    FilterMatchDecision.match.name()},
            {SCRIPT_URL,              BASE_URL,                FilterMatchDecision.no_match.name()},
            {TRACKER_URL,             SCRIPT_URL,              FilterMatchDecision.abort_not_found.name()},
            {TRACKER_URL,             SCRIPT_URL,              FilterMatchDecision.abort_not_found.name()},
            {TRACKER_URL,             SCRIPT_URL,              FilterMatchDecision.abort_not_found.name()}
    };

    @Test public void testNestedBlockWithRepeat () throws Exception {
        final BlockStatsSummary summary = runTest(NESTED_BLOCK_WITH_REPEAT_TEST);
        assertEquals("expected total == 3", 3, summary.getTotal());
        assertEquals("expected 1 blocked fqdn", 1, summary.getBlocks().size());
        assertEquals("expected 3 blocks for fqdn", 3, findBlock(summary, TRACKER_FQDN).getCount());
    }

    @Test public void testComplexLiveExample () throws Exception {
        final BlockStatRecord rec = json(stream2string("models/tests/filter/blockStatRecord.json"), BlockStatRecord.class).init();
        final BlockStatsSummary summary = rec.summarize();
        assertEquals("expected 11 total", 11, summary.getTotal());
        assertEquals("expected 1 googletagmanager block", 1, findBlock(summary, "www.googletagmanager.com").getCount());
        assertEquals("expected 4 googleads.g.doubleclick blocks", 4, findBlock(summary, "googleads.g.doubleclick.net").getCount());
        assertEquals("expected 2 static.doubleclick blocks", 2, findBlock(summary, "static.doubleclick.net").getCount());
        assertEquals("expected 2 youtube blocks", 2, findBlock(summary, "www.youtube.com").getCount());
        assertEquals("expected 1 d1z2jf7jlzjs58.cloudfront.net block", 1, findBlock(summary, "d1z2jf7jlzjs58.cloudfront.net").getCount());
        assertEquals("expected 1 pub.network block", 1, findBlock(summary, "a.pub.network").getCount());
    }

    public BlockStatsSummary runTest(String[][] test) {
        String reqId = null;
        for (String[] rec : test) {
            final String id = record(STATS_SERVICE, rec[0], rec[1], FilterMatchDecision.valueOf(rec[2]));
            if (reqId == null) reqId = id;
        }
        return STATS_SERVICE.getSummary(reqId);
    }

    public BlockStatsSummary.FqdnBlockCount findBlock(BlockStatsSummary summary, String fqdn) {
        final List<BlockStatsSummary.FqdnBlockCount> blocks = summary.getBlocks().stream()
                .filter(b -> b.getFqdn().equals(fqdn))
                .collect(Collectors.toList());
        assertEquals("fqdn not found in blocks: "+fqdn, 1, blocks.size());
        return blocks.get(0);
    }

    public String record(BlockStatsService svc, String url, String referer, FilterMatchDecision decision) {
        final String fqdn = URIUtil.getHost(url);
        final String uri = URIUtil.getPath(url);
        final String requestId = fqdn + "." + randomUUID().toString();
        svc.record(new FilterMatchersRequest()
                        .setRequestId(requestId)
                        .setFqdn(fqdn)
                        .setUri(uri)
                        .setReferer(referer)
                        .setUserAgent("ua"),
                decision);
        return requestId;
    }

}
