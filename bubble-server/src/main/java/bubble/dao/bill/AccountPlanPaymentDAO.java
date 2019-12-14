package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPlanPayment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountPlanPaymentDAO extends AccountOwnedEntityDAO<AccountPlanPayment> {

    public AccountPlanPayment findByBill(String billUuid) { return findByUniqueField("bill", billUuid); }

    public AccountPlanPayment findByAccountPaymentAndBill(String planPaymentMethod, String billUuid) {
        return findByUniqueFields("planPaymentMethod", planPaymentMethod, "bill", billUuid);
    }

    public List<AccountPlanPayment> findByAccountPayment(String accountPayment) {
        return findByField("payment", accountPayment);
    }

    public List<AccountPlanPayment> findByAccountPaymentMethodAndPeriodAndCurrency(String paymentMethodUuid,
                                                                                   String billPeriod,
                                                                                   String currency) {
        return findByFields("paymentMethod", paymentMethodUuid, "period", billPeriod, "currency", currency);
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
