package bubble.rule.bblock.spec;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@AllArgsConstructor
public class BlockSpec {

    @Getter private BlockSpecTarget target;

    @Getter private List<String> options;
    @Getter(lazy=true) private final Pattern domainPattern = Pattern.compile(target.getDomainRegex());

    public boolean hasOptions () { return options != null && !options.isEmpty(); }

    @Getter private String selector;
    public boolean hasSelector() { return !empty(selector); }

    public boolean isBlanket() { return !hasOptions() && !hasSelector(); }

    public static List<BlockSpec> parse(String line) {

        line = line.trim();
        int optionStartPos = line.indexOf('$');
        int selectorStartPos = line.indexOf("##");

        // sanity check that selectorStartPos > optionStartPos -- $ may occur AFTER ## if the selector contains a regex
        if (selectorStartPos != -1 && optionStartPos > selectorStartPos) optionStartPos = -1;

        final List<BlockSpecTarget> targets;
        final List<String> options;
        final String selector;
        if (optionStartPos == -1) {
            if (selectorStartPos == -1) {
                // no options, no selector, entire line is the target
                targets = BlockSpecTarget.parse(line);
                options = null;
                selector = null;
            } else {
                // no options, but selector present. split into target + selector
                targets = BlockSpecTarget.parse(line.substring(0, selectorStartPos));
                options = null;
                selector = line.substring(selectorStartPos+1);
            }
        } else {
            if (selectorStartPos == -1) {
                // no selector, split into target + options
                targets = BlockSpecTarget.parse(line.substring(0, optionStartPos));
                options = StringUtil.splitAndTrim(line.substring(optionStartPos+1), ",");
                selector = null;
            } else {
                // all 3 elements present
                targets = BlockSpecTarget.parse(line.substring(0, optionStartPos));
                options = StringUtil.splitAndTrim(line.substring(optionStartPos + 1, selectorStartPos), ",");
                selector = line.substring(selectorStartPos+1);
            }
        }
        final List<BlockSpec> specs = new ArrayList<>();
        for (BlockSpecTarget target : targets) specs.add(new BlockSpec(target, options, selector));
        return specs;
    }

    public boolean matches(String fqdn, String path) {
        if (getDomainPattern().matcher(fqdn).find()) {
            return true;
        }
        return false;
    }
}
