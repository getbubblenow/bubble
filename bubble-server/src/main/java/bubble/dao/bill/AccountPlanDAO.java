package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.AccountPlanPaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository
public class AccountPlanDAO extends AccountOwnedEntityDAO<AccountPlan> {

    @Autowired private AccountPlanPaymentMethodDAO accountPlanPaymentMethodDAO;

    public AccountPlan findByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByUniqueFields("account", accountUuid, "network", networkUuid);
    }

    @Override public Object preCreate(AccountPlan accountPlan) {
        if (!accountPlan.hasPaymentMethod()) throw invalidEx("err.paymentMethod.required");
        return super.preCreate(accountPlan);
    }

    @Override public AccountPlan postCreate(AccountPlan accountPlan, Object context) {
        accountPlanPaymentMethodDAO.create(new AccountPlanPaymentMethod()
                .setAccount(accountPlan.getAccount())
                .setAccountPlan(accountPlan.getUuid())
                .setPaymentMethod(accountPlan.getPaymentMethod().getUuid()));
        return super.postCreate(accountPlan, context);
    }

}
