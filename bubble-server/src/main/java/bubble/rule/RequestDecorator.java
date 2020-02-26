/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.rule;

import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.handlebars.HandlebarsUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor @Accessors(chain=true)
public class RequestDecorator {

    @Getter @Setter private String regex;
    @Getter @Setter private String insert;

    @Getter @Setter private String css;
    public boolean hasCss() { return css != null; }

    @Getter @Setter private String body;
    public boolean hasBody() { return body != null; }

    @Getter @Setter private String js;
    public boolean hasJs() { return js != null; }

    public String decorate(Handlebars handlebars, String data, Map<String, Object> ctx) {
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            final String updated = data.substring(0, matcher.end())
                    + HandlebarsUtil.apply(handlebars, insert, ctx)
                    + data.substring(matcher.end());
            return updated;
        }
        return data;
    }
}
