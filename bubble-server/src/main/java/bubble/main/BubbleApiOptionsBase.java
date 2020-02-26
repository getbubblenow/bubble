/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main;

import org.cobbzilla.wizard.main.MainApiOptionsBase;

public class BubbleApiOptionsBase extends MainApiOptionsBase {

    @Override protected String getPasswordEnvVarName() { return "BUBBLE_PASS"; }

    @Override protected String getDefaultApiBaseUri() { return "@BUBBLE_API"; }

    @Override protected String getDefaultAccount() { return "@BUBBLE_USER"; }

}
