/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public interface RequestModifierRule {

    RequestModifierConfig getRequestModifierConfig ();

    Class<RequestModifierRule> RMR = RequestModifierRule.class;
    String ICON_JS_TEMPLATE = stream2string(getPackagePath(RMR)+"/"+ RMR.getSimpleName()+"_icon.js.hbs");

}
