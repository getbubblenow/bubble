package bubble.rule.social.block;

import bubble.BubbleHandlebars;
import bubble.dao.app.AppDataDAOBase;
import bubble.rule.RequestDecorator;
import bubble.rule.RuleConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.cobbzilla.util.io.regex.RegexChunkConfig;
import org.cobbzilla.util.javascript.StandardJsEngine;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public class UserBlockerConfig extends RegexChunkConfig implements RuleConfig {

    public static final StandardJsEngine STANDARD_JS_ENGINE = new StandardJsEngine();

    @Getter @Setter private String blockedCommentCheck;
    @Getter @Setter private String blockedCommentReplacement;
    public boolean hasBlockedCommentReplacement () { return blockedCommentReplacement != null && blockedCommentReplacement.length() > 0; }

    @Getter @Setter private RequestDecorator commentDecorator;
    public boolean hasCommentDecorator() { return commentDecorator != null; }

    @Getter @Setter private String app;
    @Getter @Setter private String rule;
    @Getter @Setter private String matcher;
    @Getter @Setter private String driver;

    @Getter @Setter private String hasHandlebarsClass = BubbleHandlebars.class.getName();

    @JsonIgnore @Getter(lazy=true) private final Handlebars handlebars
            = ((HasHandlebars) instantiate(getHasHandlebarsClass())).getHandlebars();

    @Getter @Setter private String ruleDataDAOClass;
    @Getter @Setter private String ruleDataDAOConfig;

    @JsonIgnore public AppDataDAOBase getDataDAO() {
        final AppDataDAOBase dao = instantiate(ruleDataDAOClass);
        dao.configure(json(ruleDataDAOConfig, JsonNode.class));
        return dao;
    }

    @Getter @Setter private String debugSite;
    public boolean debugSite(String site) { return debugSite != null && debugSite.equals(site); }

    @Getter @Setter private UserBlockerUserConfig userConfig;

}
