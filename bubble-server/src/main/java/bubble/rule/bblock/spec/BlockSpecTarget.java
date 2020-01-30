package bubble.rule.bblock.spec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@NoArgsConstructor @Accessors(chain=true)
public class BlockSpecTarget {

    @Getter @Setter private String domainRegex;
    @Getter @Setter private String regex;

    public static List<BlockSpecTarget> parse(String data) {
        final List<BlockSpecTarget> targets = new ArrayList<>();
        for (String part : data.split(",")) {
            targets.add(parseTarget(part));
        }
        return targets;
    }

    private static BlockSpecTarget parseTarget(String data) {
        String domainRegex = null;
        String regex = null;
        if (data.startsWith("||")) {
            final int caretPos = data.indexOf("^");
            if (caretPos != -1) {
                domainRegex = ".*?"+Pattern.quote(data.substring(2, caretPos))+"$";
            } else {

            }
        }
        return new BlockSpecTarget().setDomainRegex(domainRegex).setRegex(regex);
    }

}
