/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.BubbleHandlebars;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.main.MainBase;

import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

public class BubbleHandlebarsMain extends MainBase<BubbleHandlebarsOptions> {

    public static void main (String[] args) { main(BubbleHandlebarsMain.class, args); }

    @Override protected void run() throws Exception {
        final BubbleHandlebarsOptions opts = getOptions();
        final Map<String, Object> ctx = new HashMap<>(System.getenv());
        if (opts.hasAdditionalContext()) {
            ctx.put(opts.getAdditionalContextName(), fromJsonOrDie(opts.getAdditionalContext(), forName(opts.getAdditionalContextClass())));
        }
        out(HandlebarsUtil.apply(BubbleHandlebars.instance.getHandlebars(), readStdin(), ctx));
    }

}
