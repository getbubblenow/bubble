/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import org.cobbzilla.wizard.main.ModelSetupOptionsBase;

import static bubble.ApiConstants.ENTITY_CONFIGS_ENDPOINT;

public class BubbleModelOptions extends ModelSetupOptionsBase {

    @Override protected String getDefaultAccount() { return "@BUBBLE_USER"; }

    @Override protected String getPasswordEnvVarName() { return "BUBBLE_PASS"; }

    @Override protected String getDefaultEntityConfigUrl() { return getApiBase() + ENTITY_CONFIGS_ENDPOINT; }

    @Override protected String getDefaultApiBaseUri() { return "@BUBBLE_API"; }

}
