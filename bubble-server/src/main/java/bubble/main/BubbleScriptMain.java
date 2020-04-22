/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.server.BubbleConfiguration;

import java.util.Map;

import static bubble.ApiConstants.getBubbleDefaultDomain;

public class BubbleScriptMain extends BubbleScriptMainBase<BubbleScriptOptions> {

    public static final BubbleConfiguration DEFAULT_BUBBLE_CONFIG = new BubbleConfiguration();

    public static void main (String[] args) { main(BubbleScriptMain.class, args); }

    @Override protected void setScriptContextVars(Map<String, Object> ctx) {
        ctx.put("defaultDomain", getBubbleDefaultDomain());
        ctx.put("serverConfig", DEFAULT_BUBBLE_CONFIG);
        super.setScriptContextVars(ctx);
    }
}
