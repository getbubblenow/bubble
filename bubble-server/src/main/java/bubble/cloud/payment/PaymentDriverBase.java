package bubble.cloud.payment;

import bubble.cloud.CloudServiceDriverBase;
import bubble.dao.bill.*;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.*;
import bubble.service.bill.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static bubble.model.bill.AccountPayment.totalPayments;
import static bubble.model.bill.PaymentMethodType.promotional_credit;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public abstract class PaymentDriverBase<T> extends CloudServiceDriverBase<T> implements PaymentServiceDriver {

    @Autowired protected AccountPlanDAO accountPlanDAO;
    @Autowired protected BubblePlanDAO planDAO;
    @Autowired protected AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired protected BillDAO billDAO;
    @Autowired protected AccountPaymentDAO accountPaymentDAO;
    @Autowired protected PromotionDAO promotionDAO;
    @Autowired protected PromotionService promoService;
    @Autowired protected CloudServiceDAO cloudDAO;

    protected abstract ChargeResult charge(BubblePlan plan,
                                           AccountPlan accountPlan,
                                           AccountPaymentMethod paymentMethod,
                                           Bill bill,
                                           long chargeAmount);

    protected abstract String refund(AccountPlan accountPlan,
                                     AccountPayment payment,
                                     AccountPaymentMethod paymentMethod,
                                     Bill bill,
                                     long refundAmount);

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

    public Bill getBill(String billUuid, long purchaseAmount, String currency, AccountPlan accountPlan) {
        final Bill bill = billDAO.findByUuid(billUuid);
        if (bill == null) throw invalidEx("err.purchase.billNotFound");
        if (!bill.getAccount().equals(accountPlan.getAccount())) throw invalidEx("err.purchase.accountMismatch");
        if (bill.getTotal() != purchaseAmount) throw invalidEx("err.purchase.amountMismatch");
        if (!bill.getCurrency().equals(currency)) throw invalidEx("err.purchase.currencyMismatch");
        return bill;
    }

    @Override public boolean authorize(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        return true;
    }

    @Override public boolean cancelAuthorization(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        return true;
    }

    @Override public boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid) {

        final AccountPlan accountPlan = getAccountPlan(accountPlanUuid);
        final BubblePlan plan = getBubblePlan(accountPlan);
        final AccountPaymentMethod paymentMethod = getPaymentMethod(accountPlan, paymentMethodUuid);
        final Bill bill = getBill(billUuid, plan.getPrice(), plan.getCurrency(), accountPlan);

        if (!paymentMethod.getAccount().equals(accountPlan.getAccount()) || !paymentMethod.getAccount().equals(bill.getAccount())) {
            throw invalidEx("err.purchase.billNotFound");
        }

        // has this already been paid?
        if (bill.paid()) {
            log.warn("purchase: existing Bill was already paid (returning true): " + bill.getUuid());
            return true;
        }

        final AccountPayment successfulPayment = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndPaymentSuccess(accountPlan.getAccount(), accountPlanUuid, bill.getUuid());
        if (successfulPayment != null) {
            log.warn("purchase: successful AccountPayment found (marking Bill "+bill.getUuid()+" as paid and returning true): " + successfulPayment.getUuid());
            billDAO.update(bill.setStatus(BillStatus.paid));
            return true;
        }

        // If the current PaymentDriver is not for a promotional credit,
        // then check for AccountPaymentMethods associated with promotional credits
        // If we have one, use that payment driver instead. It may apply a partial payment.
        long chargeAmount = bill.getTotal();
        if (getPaymentMethodType() != promotional_credit) {
            final List<AccountPayment> creditsApplied = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, billUuid);
            // sanity check
            if (totalPayments(creditsApplied) >= bill.getTotal()) {
                log.warn("purchase: credit already applied sufficient to pay bill.total");
            } else {
                final List<AccountPaymentMethod> accountPaymentMethods = paymentMethodDAO.findByAccountAndPromoAndNotDeleted(accountPlan.getAccount());
                final Map<Promotion, AccountPaymentMethod> promos = new TreeMap<>();
                for (AccountPaymentMethod apm : accountPaymentMethods) {
                    if (!apm.hasPromotion()) {
                        log.warn("purchase: AccountPaymentMethod "+apm.getUuid()+" has type "+promotional_credit+" but promotion was null, skipping");
                        continue;
                    }
                    final Promotion promo = promotionDAO.findByUuid(apm.getPromotion());
                    if (promo == null) {
                        log.warn("purchase: AccountPaymentMethod "+apm.getUuid()+": promotion "+apm.getPromotion()+" does not exist");
                        continue;
                    }
                    if (promo.inactive()) {
                        log.warn("purchase: AccountPaymentMethod "+apm.getUuid()+": promotion "+apm.getPromotion()+" is not active");
                        continue;
                    }
                    promos.put(promo, apm);
                }
                if (!promos.isEmpty()) {
                    chargeAmount = promoService.usePromotions(plan, accountPlan, bill, paymentMethod, this, promos, chargeAmount);
                    if (chargeAmount <= 0) {
                        log.info("purchase: chargeAmount="+chargeAmount+" after applying promotions, done");
                        return true;
                    }
                }
            }
        }

        final ChargeResult chargeResult;
        try {
            chargeResult = charge(plan, accountPlan, paymentMethod, bill, chargeAmount);
        } catch (RuntimeException e) {
            // record failed payment, rethrow
            accountPaymentDAO.create(new AccountPayment()
                    .setType(AccountPaymentType.payment)
                    .setAccount(accountPlan.getAccount())
                    .setPlan(accountPlan.getPlan())
                    .setAccountPlan(accountPlan.getUuid())
                    .setPaymentMethod(paymentMethod.getUuid())
                    .setBill(bill.getUuid())
                    .setAmount(chargeAmount)
                    .setCurrency(bill.getCurrency())
                    .setStatus(AccountPaymentStatus.failure)
                    .setError(e)
                    .setInfo(paymentMethod.getPaymentInfo()));
            throw e;
        }
        recordPayment(bill, accountPlan, paymentMethod, chargeResult);
        return true;
    }

    public AccountPayment recordPayment(Bill bill,
                                        AccountPlan accountPlan,
                                        AccountPaymentMethod paymentMethod,
                                        ChargeResult chargeResult) {
        // record the payment
        final AccountPayment accountPayment = accountPaymentDAO.create(new AccountPayment()
                .setType(paymentMethod.getPaymentMethodType() == promotional_credit ? AccountPaymentType.credit_applied : AccountPaymentType.payment)
                .setAccount(accountPlan.getAccount())
                .setPlan(accountPlan.getPlan())
                .setAccountPlan(accountPlan.getUuid())
                .setPaymentMethod(paymentMethod.getUuid())
                .setBill(bill.getUuid())
                .setAmount(chargeResult.getAmountCharged())
                .setCurrency(bill.getCurrency())
                .setStatus(AccountPaymentStatus.success)
                .setInfo(chargeResult.getChargeId()));

        // mark the bill as paid, enable the plan
        billDAO.update(bill.setStatus(BillStatus.paid));

        // if there are no unpaid bills, we can (re-)enable the plan
        final List<Bill> unpaidBills = billDAO.findUnpaidByAccountPlan(accountPlan.getUuid());
        if (unpaidBills.isEmpty()) {
            accountPlanDAO.update(accountPlan.setEnabled(true));
        } else {
            accountPlanDAO.update(accountPlan.setEnabled(false));
        }
        return accountPayment;
    }

    @Override public boolean refund(String accountPlanUuid) {
        final AccountPlan accountPlan = accountPlanDAO.findByUuid(accountPlanUuid);
        if (accountPlan == null) {
            log.warn("refund: accountPlan not found: "+accountPlanUuid);
            throw invalidEx("err.accountPlan.notFound");
        }

        // Find most recent Bill
        final Bill bill = billDAO.findMostRecentBillForAccountPlan(accountPlanUuid);
        if (bill == null) {
            log.warn("refund: no recent bill found for accountPlan: "+accountPlanUuid);
            throw invalidEx("err.refund.billNotFound");
        }

        // Was the recent bill paid?
        final AccountPayment successfulPayment = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndPaymentSuccess(accountPlan.getAccount(), accountPlanUuid, bill.getUuid());
        if (bill.unpaid()) {
            if (successfulPayment != null) {
                // should never happen
                throw invalidEx("err.refund.unpaidBillHasPayment");
            }
            log.warn("refund: not refunding unpaid bill ("+bill.getUuid()+") accountPlan: "+accountPlanUuid);
            return false;
        }

        // What payment was used to pay the bill?
        if (successfulPayment == null) {
            log.warn("refund: AccountPlanPayment not found for paid bill ("+bill.getUuid()+") accountPlan: "+accountPlanUuid);
            throw invalidEx("err.refund.paymentNotFound");
        }

        // Is the payment method associated with the bill still active?
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(successfulPayment.getPaymentMethod());
        if (paymentMethod == null || paymentMethod.deleted()) {
            log.warn("refund: cannot refund: AccountPaymentMethod not found or deleted for paid bill ("+bill.getUuid()+") accountPlan: "+accountPlanUuid);
            throw invalidEx("err.refund.paymentMethodNotFound");
        }

        // Find the plan
        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        if (plan == null) {
            log.warn("refund: BubblePlan not found ("+accountPlan.getPlan()+") for accountPlan: "+accountPlanUuid);
            throw invalidEx("err.refund.planNotFound");
        }

        // Determine how much to refund
        long refundAmount = plan.getPeriod().calculateRefund(bill, accountPlan);
        if (refundAmount == 0) {
            log.warn("refund: no refund to issue, refundAmount == 0");
            return true;
        }

        // If a promotional credit was applied to the Bill, subtract that from the refundAmount
        final List<AccountPayment> creditsApplied = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, bill.getUuid());
        if (!empty(creditsApplied)) {
            final int totalCredits = totalPayments(creditsApplied);
            log.warn("refund: reducing refund amount of "+refundAmount+" by credit applied "+totalCredits);
            refundAmount -= totalCredits;
        }

        final String refundInfo;
        try {
            refundInfo = refund(accountPlan, successfulPayment, paymentMethod, bill, refundAmount);
        } catch (RuntimeException e) {
            // record failed payment, rethrow
            accountPaymentDAO.create(new AccountPayment()
                    .setType(AccountPaymentType.refund)
                    .setAccount(accountPlan.getAccount())
                    .setPlan(accountPlan.getPlan())
                    .setAccountPlan(accountPlan.getUuid())
                    .setPaymentMethod(paymentMethod.getUuid())
                    .setBill(bill.getUuid())
                    .setAmount(bill.getTotal())
                    .setCurrency(bill.getCurrency())
                    .setStatus(AccountPaymentStatus.failure)
                    .setError(e)
                    .setInfo(paymentMethod.getPaymentInfo()));
            throw e;
        }
        recordRefund(bill, accountPlan, paymentMethod, refundAmount, refundInfo);
        return true;
    }

    private void recordRefund(Bill bill,
                              AccountPlan accountPlan,
                              AccountPaymentMethod paymentMethod,
                              long refundAmount,
                              String refundInfo) {
        // record the payment
        final AccountPayment accountPayment = accountPaymentDAO.create(new AccountPayment()
                .setType(AccountPaymentType.refund)
                .setAccount(accountPlan.getAccount())
                .setPlan(accountPlan.getPlan())
                .setAccountPlan(accountPlan.getUuid())
                .setPaymentMethod(paymentMethod.getUuid())
                .setBill(bill.getUuid())
                .setAmount(refundAmount)
                .setCurrency(bill.getCurrency())
                .setStatus(AccountPaymentStatus.success)
                .setInfo(refundInfo));

        // update bill
        billDAO.update(bill.setRefundedAmount(refundAmount));

        // close the plan
        accountPlanDAO.update(accountPlan.setClosed(true));
    }

}
