/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.cloud;

import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleFootprint;
import bubble.resources.account.AccountOwnedTemplateResource;

public class FootprintsResourceBase extends AccountOwnedTemplateResource<BubbleFootprint, BubbleFootprintDAO> {

    public FootprintsResourceBase(Account account) { super(account); }

}
