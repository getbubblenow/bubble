package bubble.rule.bblock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"urlRegex", "userAgentRegex"})
public class BubbleUserAgentBlock {

    public static final BubbleUserAgentBlock[] NO_BLOCKS = new BubbleUserAgentBlock[0];

    @Getter(lazy=true) private final String id = sha256_hex(getUrlRegex()+"\t"+getUserAgentRegex());

    @Getter @Setter private String urlRegex;
    public boolean hasUrlRegex () { return !empty(urlRegex); }

    @JsonIgnore @Getter(lazy=true) private final Pattern urlPattern = Pattern.compile(getUrlRegex(), Pattern.CASE_INSENSITIVE);
    public boolean urlMatches(String uri) { return getUrlPattern().matcher(uri).find(); }

    @Getter @Setter private String userAgentRegex;

    @JsonIgnore @Getter(lazy=true) private final Pattern uaPattern = Pattern.compile(getUserAgentRegex(), Pattern.CASE_INSENSITIVE);
    public boolean userAgentMatches (String ua) { return getUaPattern().matcher(ua).find(); }

}
