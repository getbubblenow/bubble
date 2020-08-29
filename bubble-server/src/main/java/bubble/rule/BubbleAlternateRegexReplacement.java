package bubble.rule;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@ToString
public class BubbleAlternateRegexReplacement extends BubbleRegexReplacement {

    @Getter @Setter private String fqdnMatch;

    @Getter(lazy=true) private final Pattern pattern = Pattern.compile(fqdnMatch, CASE_INSENSITIVE);

    public boolean matches (String fqdn) { return getPattern().matcher(fqdn).matches(); }

}
