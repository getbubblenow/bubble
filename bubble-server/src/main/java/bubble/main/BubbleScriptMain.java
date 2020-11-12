/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static bubble.ApiConstants.getBubbleDefaultDomain;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Slf4j
public class BubbleScriptMain extends BubbleScriptMainBase<BubbleScriptOptions> {

    public static final BubbleConfiguration DEFAULT_BUBBLE_CONFIG = new BubbleConfiguration();

    public static void main (String[] args) { main(BubbleScriptMain.class, args); }

    @Override protected void setScriptContextVars(Map<String, Object> ctx) {
        try {
            ctx.put("defaultDomain", getBubbleDefaultDomain());
        } catch (Exception e) {
            log.warn("setScriptContextVars: no default domain found: "+shortError(e));
        }
        ctx.put("serverConfig", DEFAULT_BUBBLE_CONFIG);
        super.setScriptContextVars(ctx);
    }
}
