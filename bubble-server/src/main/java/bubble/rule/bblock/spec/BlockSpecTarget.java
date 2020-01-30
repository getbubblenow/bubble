package bubble.rule.bblock.spec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpSchemes.stripScheme;

@NoArgsConstructor @Accessors(chain=true)
public class BlockSpecTarget {

    @Getter @Setter private String domainRegex;
    public boolean hasDomainRegex() { return !empty(domainRegex); }
    @Getter(lazy=true) private final Pattern domainPattern = hasDomainRegex() ? Pattern.compile(getDomainRegex()) : null;

    @Getter @Setter private String regex;
    public boolean hasRegex() { return !empty(regex); }
    @Getter(lazy=true) private final Pattern regexPattern = hasRegex() ? Pattern.compile(getRegex()) : null;

    public static List<BlockSpecTarget> parse(String data) {
        final List<BlockSpecTarget> targets = new ArrayList<>();
        for (String part : data.split(",")) {
            targets.add(parseTarget(part));
        }
        return targets;
    }

    public static List<BlockSpecTarget> parseBareLine(String data) {
        if (data.contains("|") || data.contains("/") || data.contains("^")) return parse(data);

        final List<BlockSpecTarget> targets = new ArrayList<>();
        for (String part : data.split(",")) {
            targets.add(new BlockSpecTarget().setDomainRegex(matchDomainOrAnySubdomains(part)));
        }
        return targets;
    }

    private static BlockSpecTarget parseTarget(String data) {
        String domainRegex = null;
        String regex = null;
        if (data.startsWith("||")) {
            final int caretPos = data.indexOf("^");
            if (caretPos != -1) {
                // domain match
                final String domain = data.substring(2, caretPos);
                domainRegex = matchDomainOrAnySubdomains(domain);
            } else {
                final String domain = data.substring(2);
                domainRegex = matchDomainOrAnySubdomains(domain);
            }
        } else if (data.startsWith("|") && data.endsWith("|")) {
            // exact match
            final String verbatimMatch = stripScheme(data.substring(1, data.length() - 1));
            regex = "^" + Pattern.quote(verbatimMatch) + "$";

        } else if (data.startsWith("/")) {
            // path match, possibly regex
            if (data.endsWith("/") && (
                    data.contains("|") || data.contains("?")
                            || (data.contains("(") && data.contains(")"))
                            || (data.contains("{") && data.contains("}")))) {
                regex = data.substring(1, data.length()-1);

            } else if (data.contains("*")) {
                regex = parseWildcardMatch(data);
            } else {
                regex = "^" + Pattern.quote(data) + ".*";
            }

        } else {
            if (data.contains("*")) {
                regex = parseWildcardMatch(data);
            } else {
                regex = "^" + Pattern.quote(data) + ".*";
            }
        }
        return new BlockSpecTarget().setDomainRegex(domainRegex).setRegex(regex);
    }

    private static String parseWildcardMatch(String data) {
        final StringBuilder b = new StringBuilder("^");
        final StringTokenizer st = new StringTokenizer(data, "*", true);
        while (st.hasMoreTokens()) {
            final String token = st.nextToken();
            b.append(token.equals("*") ? ".*?" : token);
        }
        return b.append("$").toString();
    }

    private static String matchDomainOrAnySubdomains(String domain) {
        return ".*?"+ Pattern.quote(domain)+"$";
    }

}
