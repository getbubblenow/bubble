package bubble.rule.bblock.spec;

import bubble.resources.stream.FilterMatchResponse;
import bubble.rule.FilterMatchDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@NoArgsConstructor @Accessors(chain=true)
public class BlockDecision {

    public static final BlockDecision BLOCK = new BlockDecision().setDecisionType(BlockDecisionType.block);
    public static final BlockDecision ALLOW = new BlockDecision().setDecisionType(BlockDecisionType.allow);

    @Getter @Setter BlockDecisionType decisionType = BlockDecisionType.allow;
    @Getter @Setter List<BlockSpec> specs;

    public BlockDecision add(BlockSpec spec) {
        if (specs == null) specs = new ArrayList<>();
        specs.add(spec);
        if (decisionType != BlockDecisionType.block && (spec.hasTypeMatches() || spec.hasSelector())) {
            decisionType = BlockDecisionType.filter;
        }
        return this;
    }

    public FilterMatchResponse getFilterMatchResponse() {
        switch (decisionType) {
            case block: return FilterMatchResponse.ABORT_NOT_FOUND;
            case allow: return FilterMatchResponse.NO_MATCH;
            case filter: return new FilterMatchResponse()
                    .setDecision(FilterMatchDecision.match)
                    .setFilters(specs == null ? null : getSpecs().stream().map(BlockSpec::getLine).collect(Collectors.toList()));
        }
        return die("getFilterMatchResponse: invalid decisionType: "+decisionType);
    }
}
