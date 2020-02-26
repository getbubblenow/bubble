/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service;

import org.cobbzilla.util.javascript.JsEngineConfig;
import org.cobbzilla.wizard.javascript.JavaScriptService;
import org.springframework.stereotype.Service;

@Service
public class BubbleJsService extends JavaScriptService {

    private static final JsEngineConfig JS_CONFIG = new JsEngineConfig()
            .setMinEngines(1)
            .setMaxEngines(100);

    @Override public JsEngineConfig getConfig() { return JS_CONFIG; }

}
