/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.block;

import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTPS;
import static org.cobbzilla.util.http.HttpSchemes.stripScheme;
import static org.cobbzilla.util.http.URIUtil.getHost;

@Slf4j @NoArgsConstructor
public class BlockStatRecord {

    @Getter @Setter private String requestId;
    @Getter @Setter private String referer;
    @Getter @Setter private String url;
    @Getter @Setter private FilterMatchDecision decision;
    @Getter private final List<BlockStatRecord> childRecords = new ArrayList<>(5);

    @JsonIgnore @Getter @Setter private String device;
    @JsonIgnore @Getter @Setter private String fqdn;
    @JsonIgnore @Getter @Setter private String userAgent;

    public BlockStatRecord(FilterMatchersRequest filter, FilterMatchDecision decision) {
        this.requestId = filter.getRequestId();
        this.device = filter.getDevice();
        this.referer = filter.getReferer();
        this.fqdn = filter.getFqdn();
        this.url = filter.getUrl();
        this.userAgent = filter.getUserAgent();
        this.decision = decision;
    }

    public void addChild(BlockStatRecord rec) {
        synchronized (childRecords) {
            childRecords.add(rec);
        }
    }

    public BlockStatsSummary summarize() {
        return summarize(new BlockStatsSummary());
    }

    private BlockStatsSummary summarize(BlockStatsSummary summary) {
        if (decision.isAbort()) {
            summary.addBlock(this);
        }
        for (BlockStatRecord child : childRecords) {
            child.summarize(summary);
        }
        return summary;
    }

    public BlockStatRecord init() {
        setFqdn(getHost(SCHEME_HTTPS+stripScheme(url)));
        for (BlockStatRecord child : childRecords) child.init();
        return this;
    }
}
