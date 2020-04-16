/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.model.account.HasAccount;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import org.cobbzilla.wizard.model.ModelSetupService;
import org.cobbzilla.wizard.model.Identifiable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.ApiConstants.ENTITY_CONFIGS_ENDPOINT;

@Service
public class BubbleModelSetupService extends ModelSetupService {

    @Getter @Autowired private BubbleConfiguration configuration;

    @Override protected String getEntityConfigsEndpoint() { return ENTITY_CONFIGS_ENDPOINT; }

    @Override protected void setOwner(Identifiable account, Identifiable entity) {
        if (entity instanceof HasAccount) ((HasAccount) entity).setAccount(account.getUuid());
    }

}
