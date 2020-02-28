/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.RuleDriver;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.springframework.stereotype.Repository;

@Repository
public class RuleDriverDAO extends AccountOwnedTemplateDAO<RuleDriver> {

    @Override public Object preCreate(RuleDriver driver) {
        if (driver.getVersion() == null) driver.setVersion(new SemanticVersion());
        return super.preCreate(driver);
    }

    @Override public RuleDriver findByAccountAndId(String accountUuid, String id) {
        final RuleDriver found = super.findByAccountAndId(accountUuid, id);
        return found != null
                ? found
                : findByUniqueFields("account", accountUuid, "driverClass", id);
    }

}
