package bubble.main;

import bubble.ApiConstants;
import bubble.server.BubbleConfiguration;

import java.util.Map;

public class BubbleScriptMain extends BubbleScriptMainBase<BubbleScriptOptions> {

    public static final BubbleConfiguration DEFAULT_BUBBLE_CONFIG = new BubbleConfiguration();

    public static void main (String[] args) { main(BubbleScriptMain.class, args); }

    @Override protected void setScriptContextVars(Map<String, Object> ctx) {
        ctx.put("defaultDomain", ApiConstants.getBubbleDefaultDomain());
        ctx.put("serverConfig", DEFAULT_BUBBLE_CONFIG);
        super.setScriptContextVars(ctx);
    }
}
