/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble;

import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.HasHandlebars;

import static org.cobbzilla.wizard.client.script.ApiRunner.standardHandlebars;

public class BubbleHandlebars implements HasHandlebars {

    public static final BubbleHandlebars instance = new BubbleHandlebars();

    @Getter(lazy=true) private final Handlebars handlebars = initHandlebars();
    private Handlebars initHandlebars() {
        return standardHandlebars(new Handlebars(new HandlebarsUtil(ApiConstants.class.getSimpleName())));
    }

}
