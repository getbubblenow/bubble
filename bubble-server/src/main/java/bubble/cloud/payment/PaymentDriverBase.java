package bubble.cloud.payment;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.CloudServiceType;
import bubble.dao.bill.*;
import bubble.model.bill.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public abstract class PaymentDriverBase<T> extends CloudServiceDriverBase<T> implements PaymentServiceDriver {

    @Override public CloudServiceType getType() { return CloudServiceType.payment; }

    @Autowired protected AccountPlanDAO accountPlanDAO;
    @Autowired protected BubblePlanDAO planDAO;
    @Autowired protected AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired protected AccountPlanPaymentMethodDAO planPaymentMethodDAO;
    @Autowired protected BillDAO billDAO;
    @Autowired protected AccountPaymentDAO accountPaymentDAO;
    @Autowired protected AccountPlanPaymentDAO accountPlanPaymentDAO;

    public AccountPlan getAccountPlan(String accountPlanUuid) {
        final AccountPlan accountPlan = accountPlanDAO.findByUuid(accountPlanUuid);
        if (accountPlan == null) throw invalidEx("err.purchase.planNotFound");
        return accountPlan;
    }

    public BubblePlan getBubblePlan(AccountPlan accountPlan) {
        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        if (plan == null) throw invalidEx("err.purchase.planNotFound");
        return plan;
    }

    public AccountPaymentMethod getPaymentMethod(AccountPlan accountPlan, String paymentMethodUuid) {
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentMethodUuid);
        if (paymentMethod == null) throw invalidEx("err.purchase.paymentMethodNotFound");
        if (!paymentMethod.getAccount().equals(accountPlan.getAccount())) throw invalidEx("err.purchase.accountMismatch");
        if (paymentMethod.getPaymentMethodType() != getPaymentMethodType()) throw invalidEx("err.purchase.paymentMethodMismatch");
        return paymentMethod;
    }

    public AccountPlanPaymentMethod getPlanPaymentMethod(String accountPlanUuid, String paymentMethodUuid) {
        final AccountPlanPaymentMethod planPaymentMethod = planPaymentMethodDAO.findCurrentMethodForPlan(accountPlanUuid);
        if (planPaymentMethod == null) throw invalidEx("err.purchase.paymentMethodNotSet");
        if (!planPaymentMethod.getPaymentMethod().equals(paymentMethodUuid)) throw invalidEx("err.purchase.paymentMethodMismatch");
        return planPaymentMethod;
    }

    public Bill getBill(String billUuid, long purchaseAmount, String currency, AccountPlan accountPlan) {
        final Bill bill = billDAO.findByUuid(billUuid);
        if (bill == null) throw invalidEx("err.purchase.billNotFound");
        if (!bill.getAccount().equals(accountPlan.getAccount())) throw invalidEx("err.purchase.accountMismatch");
        if (bill.getTotal() != purchaseAmount) throw invalidEx("err.purchase.amountMismatch");
        if (!bill.getCurrency().equals(currency)) throw invalidEx("err.purchase.currencyMismatch");
        return bill;
    }

    @Override public boolean authorize(BubblePlan plan, AccountPaymentMethod paymentMethod) {
        return true;
    }

    @Override public boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid) {

        final AccountPlan accountPlan = getAccountPlan(accountPlanUuid);
        final BubblePlan plan = getBubblePlan(accountPlan);
        final AccountPaymentMethod paymentMethod = getPaymentMethod(accountPlan, paymentMethodUuid);
        final Bill bill = getBill(billUuid, plan.getPrice(), plan.getCurrency(), accountPlan);
        final AccountPlanPaymentMethod planPaymentMethod = getPlanPaymentMethod(accountPlanUuid, paymentMethodUuid);

        if (!paymentMethod.getAccount().equals(accountPlan.getAccount()) || !paymentMethod.getAccount().equals(bill.getAccount())) {
            throw invalidEx("err.purchase.billNotFound");
        }

        // has this already been paid?
        final AccountPlanPayment existing = accountPlanPaymentDAO.findByBill(billUuid);
        if (existing != null) {
            log.warn("purchase: existing AccountPlanPayment found (returning true): "+existing.getUuid());
            return true;
        }

        final AccountPlanPayment priorPayment = findPriorPayment(plan, paymentMethod, bill);
        if (priorPayment != null) {
            billDAO.update(bill.setStatus(BillStatus.paid));
            accountPlanPaymentDAO.create(new AccountPlanPayment()
                    .setAccount(accountPlan.getAccount())
                    .setPlan(accountPlan.getPlan())
                    .setAccountPlan(accountPlan.getUuid())
                    .setPayment(priorPayment.getPayment())
                    .setPaymentMethod(priorPayment.getPaymentMethod())
                    .setPlanPaymentMethod(planPaymentMethod.getUuid())
                    .setBill(bill.getUuid())
                    .setPeriod(bill.getPeriod())
                    .setAmount(0L)
                    .setCurrency(bill.getCurrency()));

        } else {
            try {
                charge(plan, accountPlan, paymentMethod, planPaymentMethod, bill);
            } catch (RuntimeException e) {
                // record failed payment, rethrow
                accountPaymentDAO.create(new AccountPayment()
                        .setAccount(accountPlan.getAccount())
                        .setPlan(accountPlan.getPlan())
                        .setAccountPlan(accountPlan.getUuid())
                        .setPaymentMethod(paymentMethod.getUuid())
                        .setAmount(bill.getTotal())
                        .setCurrency(bill.getCurrency())
                        .setStatus(AccountPaymentStatus.failure)
                        .setError(e)
                        .setInfo(paymentMethod.getPaymentInfo()));
                throw e;
            }
            recordPayment(bill, accountPlan, paymentMethod, planPaymentMethod);
        }
        accountPlanDAO.update(accountPlan.setEnabled(true));
        return true;
    }

    public AccountPlanPayment findPriorPayment(BubblePlan plan, AccountPaymentMethod paymentMethod, Bill bill) {
        // is there a previous AccountPlanPayment where:
        //  - it has a price that is the same or more expensive than the BubblePlan being purchased now
        //  - it has a price in the same currency as the BubblePlan being purchased now
        //  - it has the same AccountPaymentMethod
        //  - it is for the current period
        //  - the corresponding AccountPlan has been deleted
        // if so we can re-use that payment and do not need to charge anything now
        final List<AccountPlanPayment> priorSimilarPayments = accountPlanPaymentDAO.findByAccountPaymentMethodAndPeriodAndPriceAndCurrency(
                paymentMethod.getUuid(),
                bill.getPeriod(),
                plan.getPrice(),
                plan.getCurrency());
        for (AccountPlanPayment app : priorSimilarPayments) {
            final AccountPlan ap = accountPlanDAO.findByUuid(app.getAccountPlan());
            if (ap != null && ap.deleted()) return app;
        }
        return null;
    }

    protected abstract void charge(BubblePlan plan,
                                   AccountPlan accountPlan,
                                   AccountPaymentMethod paymentMethod,
                                   AccountPlanPaymentMethod planPaymentMethod,
                                   Bill bill);

    @Override public boolean refund(String accountPlanUuid, String paymentMethodUuid, String billUuid, long refundAmount) {
        log.error("refund: not yet supported: accountPlanUuid="+accountPlanUuid+", paymentMethodUuid="+paymentMethodUuid+", billUuid="+billUuid);
        return false;
    }

    public AccountPlanPayment recordPayment(Bill bill,
                                            AccountPlan accountPlan,
                                            AccountPaymentMethod paymentMethod,
                                            AccountPlanPaymentMethod planPaymentMethod) {
        // mark the bill as paid
        billDAO.update(bill.setStatus(BillStatus.paid));

        // create the payment
        final AccountPayment payment = accountPaymentDAO.create(new AccountPayment()
                .setAccount(accountPlan.getAccount())
                .setPlan(accountPlan.getPlan())
                .setAccountPlan(accountPlan.getUuid())
                .setPaymentMethod(paymentMethod.getUuid())
                .setAmount(bill.getTotal())
                .setCurrency(bill.getCurrency())
                .setStatus(AccountPaymentStatus.success)
                .setInfo(paymentMethod.getPaymentInfo()));

        // associate the payment to the bill
        return accountPlanPaymentDAO.create(new AccountPlanPayment()
                .setAccount(accountPlan.getAccount())
                .setPlan(accountPlan.getPlan())
                .setAccountPlan(accountPlan.getUuid())
                .setPayment(payment.getUuid())
                .setPaymentMethod(paymentMethod.getUuid())
                .setPlanPaymentMethod(planPaymentMethod.getUuid())
                .setBill(bill.getUuid())
                .setPeriod(bill.getPeriod())
                .setAmount(bill.getTotal())
                .setCurrency(bill.getCurrency()));
    }

}
