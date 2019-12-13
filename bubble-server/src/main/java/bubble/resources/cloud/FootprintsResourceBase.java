package bubble.resources.cloud;

import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleFootprint;
import bubble.resources.account.AccountOwnedTemplateResource;

public class FootprintsResourceBase extends AccountOwnedTemplateResource<BubbleFootprint, BubbleFootprintDAO> {

    public FootprintsResourceBase(Account account) { super(account); }

}
