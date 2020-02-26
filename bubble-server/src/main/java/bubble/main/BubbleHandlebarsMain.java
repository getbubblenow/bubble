/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main;

import bubble.BubbleHandlebars;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.main.MainBase;

import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;

public class BubbleHandlebarsMain extends MainBase<BubbleHandlebarsOptions> {

    public static void main (String[] args) { main(BubbleHandlebarsMain.class, args); }

    @Override protected void run() throws Exception {
        final Map<String, Object> ctx = new HashMap<>(System.getenv());
        out(HandlebarsUtil.apply(BubbleHandlebars.instance.getHandlebars(), readStdin(), ctx));
    }

}
