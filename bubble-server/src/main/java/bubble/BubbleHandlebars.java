package bubble;

import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.cobbzilla.util.javascript.StandardJsEngine;

public class BubbleHandlebars implements HasHandlebars {

    public static final BubbleHandlebars instance = new BubbleHandlebars();

    @Getter(lazy=true) private final Handlebars handlebars = initHandlebars();
    private Handlebars initHandlebars() {
        final Handlebars hbs = new Handlebars(new HandlebarsUtil(ApiConstants.class.getSimpleName()));
        HandlebarsUtil.registerUtilityHelpers(hbs);
        HandlebarsUtil.registerDateHelpers(hbs);
        HandlebarsUtil.registerJavaScriptHelper(hbs, StandardJsEngine::new);
        return hbs;
    }

}
