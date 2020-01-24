package bubble.test;

import bubble.model.app.AppData;
import bubble.server.BubbleConfiguration;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.util.RestResponse;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProxyTest extends ActivatedBubbleModelTestBase {

    public static final String MANIFEST_PROXY = "manifest-proxy";

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        server.getConfiguration().getStaticAssets().setSinglePageApp(null); // disable SPA
        super.beforeStart(server);
    }

    @Override protected String getManifest() { return MANIFEST_PROXY; }

    @Test public void testSimple () throws Exception { modelTest("proxy"); }
    @Test public void testSimpleUserBlock () throws Exception { modelTest("filter/simple_user_block"); }

    @Test public void testHN () throws Exception {
        modelTest("filter/user_block/hn_request1");
        final RestResponse response = (RestResponse) getApiRunner().getContext().get("hn_response");

        // find block link for user electricEmu
        final Pattern pattern = Pattern.compile("=electricEmu.+?</span>\\s*<a\\s*href=\"(.+?)\"");
        final Matcher matcher = pattern.matcher(response.json);
        assertTrue("expected match for user to block", matcher.find());
        final String blockUrl = matcher.group(1);
        final AppData blockResponse = getApi().get(blockUrl, AppData.class);
        assertEquals("expected user block to be saved correctly", "electricEmu", blockResponse.getKey());

        // second request, verify additional user blocked
        modelTest("filter/user_block/hn_request2");
    }

}
