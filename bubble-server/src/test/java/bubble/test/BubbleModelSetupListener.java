/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test;

import bubble.server.BubbleConfiguration;
import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListenerBase;

import static org.cobbzilla.util.handlebars.HandlebarsUtil.applyReflectively;

@AllArgsConstructor
public class BubbleModelSetupListener extends ModelSetupListenerBase {

    private final BubbleConfiguration configuration;

    @Override public Identifiable subst(Identifiable entity) {
        return applyReflectively(configuration.getHandlebars(), entity, configuration.getEnvCtx());
    }

}
