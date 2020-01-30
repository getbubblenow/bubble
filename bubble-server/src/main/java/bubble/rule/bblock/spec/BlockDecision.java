package bubble.rule.bblock.spec;

import bubble.resources.stream.FilterMatchResponse;
import bubble.rule.FilterMatchDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class BlockDecision {

    public static final BlockDecision BLOCK = new BlockDecision().setDecisionType(BlockDecisionType.block);
    public static final BlockDecision ALLOW = new BlockDecision().setDecisionType(BlockDecisionType.allow);

    @Getter @Setter BlockDecisionType decisionType = BlockDecisionType.allow;
    @Getter @Setter List<String> selectors;
    @Getter @Setter List<String> options;

    public BlockDecision add(BlockSpec block) {
        if (block.hasSelector()) {
            if (selectors == null) selectors = new ArrayList<>();
            selectors.add(block.getSelector());
        }
        if (block.hasOptions()) {
            if (options == null) options = new ArrayList<>();
            options.addAll(block.getOptions());
        }
        if (!empty(selectors) || !empty(options)) decisionType = BlockDecisionType.filter;
        return this;
    }

    public FilterMatchDecision getFilterMatchDecision() {
        switch (decisionType) {
            case block: return FilterMatchDecision.abort_not_found;
            case allow: return FilterMatchDecision.no_match;
            case filter: return FilterMatchDecision.match;
        }
        return die("getFilterMatchDecision: invalid decisionType: "+decisionType);
    }

    public FilterMatchResponse getFilterMatchResponse() {
        switch (decisionType) {
            case block: return FilterMatchResponse.ABORT_NOT_FOUND;
            case allow: return FilterMatchResponse.NO_MATCH;
            case filter: return new FilterMatchResponse()
                    .setDecision(FilterMatchDecision.match)
                    .setOptions(getOptions())
                    .setSelectors(getSelectors());
        }
        return die("getFilterMatchResponse: invalid decisionType: "+decisionType);
    }
}
