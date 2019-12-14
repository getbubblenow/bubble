package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPlanPayment;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.hibernate.criterion.Restrictions.*;

@Repository
public class AccountPlanPaymentDAO extends AccountOwnedEntityDAO<AccountPlanPayment> {

    public AccountPlanPayment findByBill(String billUuid) { return findByUniqueField("bill", billUuid); }

    public AccountPlanPayment findByAccountPaymentAndBill(String planPaymentMethod, String billUuid) {
        return findByUniqueFields("planPaymentMethod", planPaymentMethod, "bill", billUuid);
    }

    public List<AccountPlanPayment> findByAccountPaymentMethodAndPeriodAndPriceAndCurrency(String paymentMethodUuid,
                                                                                           String billPeriod,
                                                                                           Long price,
                                                                                           String currency) {
        return list(criteria().add(and(
                eq("paymentMethod", paymentMethodUuid),
                eq("period", billPeriod),
                eq("currency", currency),
                ge("amount", price)
        )));
    }

    public List<AccountPlanPayment> findByAccountAndBill(String accountUuid, String billUuid) {
        return findByFields("account", accountUuid, "bill", billUuid);
    }

    public List<AccountPlanPayment> findByAccountAndAccountPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid);
    }

    public List<AccountPlanPayment> findByAccountAndAccountPlanAndBill(String accountUuid, String accountPlanUuid, String billUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid, "bill", billUuid);
    }
}
