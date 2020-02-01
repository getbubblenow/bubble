package bubble.rule.bblock;

import bubble.model.app.AppRule;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.string.StringUtil;

import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class BubbleBlockList {

    @Getter @Setter private String id;
    public boolean hasId(String id) { return this.id != null && this.id.equals(id); }

    @Getter @Setter private String name;
    @Getter @Setter private String description;
    @Getter @Setter private String[] tags;

    public String getTagString() { return arrayToString(tags, ", ", "", false); }
    public BubbleBlockList setTagString (String val) {
        return setTags(StringUtil.splitAndTrim(val, ",").toArray(StringUtil.EMPTY_ARRAY));
    }

    @Getter @Setter private String url;
    public boolean hasUrl () { return !empty(url); }

    @Getter @Setter private String[] additionalEntries;
    public boolean hasAdditionalEntries () { return !empty(additionalEntries); }

    @Getter @Setter private Boolean enabled = true;
    public boolean enabled() { return enabled != null && enabled; }

    @JsonIgnore @Getter @Setter private AppRule rule;

}
