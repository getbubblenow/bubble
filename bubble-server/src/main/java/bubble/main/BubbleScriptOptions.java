/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.main;

import org.cobbzilla.wizard.main.ScriptMainOptionsBase;

public class BubbleScriptOptions extends ScriptMainOptionsBase {

    @Override protected String getDefaultAccount() { return "@BUBBLE_USER"; }

    @Override protected String getPasswordEnvVarName() { return "BUBBLE_PASS"; }

    @Override protected String getDefaultApiBaseUri() { return "@BUBBLE_API"; }

}
