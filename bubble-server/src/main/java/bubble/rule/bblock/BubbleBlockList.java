package bubble.rule.bblock;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class BubbleBlockList {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private String description;
    @Getter @Setter private String[] tags;

    @Getter @Setter private String url;
    public boolean hasUrl () { return !empty(url); }

    @Getter @Setter private String[] additionalEntries;
    public boolean hasAdditionalEntries () { return !empty(additionalEntries); }

    @Getter @Setter private Boolean enabled = true;
    public boolean enabled() { return enabled != null && enabled; }

}
