package bubble.resources.stream;

import bubble.rule.FilterMatchDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchResponse {

    public static final FilterMatchResponse NO_MATCH = new FilterMatchResponse().setDecision(FilterMatchDecision.no_match);
    public static final FilterMatchResponse MATCH = new FilterMatchResponse().setDecision(FilterMatchDecision.match);
    public static final FilterMatchResponse ABORT_NOT_FOUND = new FilterMatchResponse().setDecision(FilterMatchDecision.abort_not_found);
    public static final FilterMatchResponse ABORT_OK = new FilterMatchResponse().setDecision(FilterMatchDecision.abort_ok);

    @Getter @Setter private FilterMatchDecision decision;

    @Getter @Setter private List<String> filters;
    public boolean hasFilters() { return !empty(filters); }

}
