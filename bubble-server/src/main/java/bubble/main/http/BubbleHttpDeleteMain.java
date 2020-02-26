/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main.http;

import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.wizard.util.RestResponse;

public class BubbleHttpDeleteMain extends BubbleHttpMain<BubbleHttpOptions> {

    public static void main (String[] args) { main(BubbleHttpDeleteMain.class, args); }

    @Override protected String getMethod() { return HttpMethods.DELETE; }

    @Override protected RestResponse request(String url) throws Exception {
        return getApiClient().delete(url);
    }

}
