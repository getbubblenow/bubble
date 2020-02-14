package bubble.cloud.payment;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.CloudServiceType;
import bubble.dao.bill.*;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.*;
import bubble.model.cloud.CloudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static bubble.model.bill.PaymentMethodType.promotional_credit;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_ASC;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public abstract class PaymentDriverBase<T> extends CloudServiceDriverBase<T> implements PaymentServiceDriver {

    @Autowired protected AccountPlanDAO accountPlanDAO;
    @Autowired protected BubblePlanDAO planDAO;
    @Autowired protected AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired protected BillDAO billDAO;
    @Autowired protected AccountPaymentDAO accountPaymentDAO;
    @Autowired protected PromotionDAO promotionDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    protected abstract String charge(BubblePlan plan,
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

    protected long applyPromotions(String accountPlanUuid, AccountPaymentMethod paymentMethod, long price) {
        // cannot apply a promotion to a promotion -- should never happen
        if (getPaymentMethodType() == promotional_credit) {
            log.warn("applyPromotions: cannot apply promotions to a promotion");
            return price;
        }

        final List<AccountPaymentMethod> promos = paymentMethodDAO.findByAccountAndPromoAndNotDeleted(paymentMethod.getAccount());
        if (!empty(promos)) {
            // sort oldest first, this ensures default promotions (like first month free) get applied before referral promotions
            promos.sort(CTIME_ASC);
            Promotion selectedPromo = null;
            for (AccountPaymentMethod apm : promos) {
                final Promotion promo = promotionDAO.findByUuid(apm.getPromotion());
                if (promo != null && promo.active()) {

                }
            }
        }


        final Bill bill = billDAO.findOldestUnpaidBillByAccountPlan(accountPlanUuid);
        final long creditApplied;
        if (bill == null) {
            log.warn("No unpaid bills for account "+paymentMethod.getAccount()+" and accountPlanUuid="+accountPlanUuid+", no credit applied");
            creditApplied = 0;
        } else {
            final AccountPayment promoPayment = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(paymentMethod.getAccount(), accountPlanUuid, bill.getUuid());
            if (promoPayment != null) {
                creditApplied = promoPayment.getAmount();
            } else {
                creditApplied = 0;
            }
        }
        if (creditApplied >= price) {
            log.info("getChargeAmount: credit applied ("+creditApplied+") exceeds price "+price+", no charge due");
            return 0;
        }
        return price - creditApplied;
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
            final AccountPayment creditAlreadyApplied = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, billUuid);
            if (creditAlreadyApplied != null) {
                log.info("purchase: credit already applied, not applying another credit");
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
                    // find the payment cloud associated with the promo, defer to that
                    final Promotion promoToUse = promos.keySet().iterator().next();
                    final AccountPaymentMethod apm = promos.get(promoToUse);
                    final CloudService promoCloud = cloudDAO.findByUuid(promoToUse.getCloud());
                    if (promoCloud == null) {
                        return die("purchase: cloud "+promoToUse.getCloud()+" not found for promotion "+promoToUse.getUuid());
                    }
                    if (promoCloud.getType() != CloudServiceType.payment) {
                        return die("purchase: cloud "+promoToUse.getCloud()+" for promotion "+promoToUse.getUuid()+" has wrong type (expected 'payment'): "+promoCloud.getType());
                    }
                    log.info("purchase: using Promotion: "+promoToUse.getName());
                    try {
                        final PaymentServiceDriver promoPaymentDriver = promoCloud.getPaymentDriver(configuration);
                        promoPaymentDriver.purchase(accountPlanUuid, apm.getUuid(), billUuid);

                        // verify AccountPayments exists for new payment with promo
                        final AccountPayment payment = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, billUuid);
                        if (payment == null) {
                            log.warn("purchase: applying promotion did not result in an AccountPayment");
                            return false;
                        }

                        if (getPaymentMethodType().requiresAuth() && !cancelAuthorization(plan, accountPlanUuid, paymentMethod)) {
                            log.warn("purchase: error cancelling authorization for accountPlanUuid=" + accountPlanUuid + ", paymentMethod=" + paymentMethod.getUuid());
                        }
                        if (payment.getAmount() >= bill.getTotal()) {
                            log.info("purchase: applying promotion paid full bill, canceled current payment authorization");
                            return true;
                        } else {
                            log.info("purchase: promotion applied credits of " + payment.getAmount() + " on a bill of " + bill.getTotal() + ", using current paymentMethod to pay the remainder; reauthorizing now...");
                            chargeAmount -= payment.getAmount();
                            if (getPaymentMethodType().requiresAuth() && !authorize(plan, accountPlanUuid, paymentMethod)) {
                                log.warn("purchase: after applying credit and cancelling previous charge authorization, new charge authorization failed");
                                return false;
                            }
                        }
                    } catch (Exception e) {
                        log.error("purchase: error applying promotion "+promoToUse.getName()+" to accountPlan "+accountPlanUuid+": "+shortError(e));
                        return false;
                    }
                }
            }
        }

        final String chargeInfo;
        try {
            chargeInfo = charge(plan, accountPlan, paymentMethod, bill, chargeAmount);
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
        recordPayment(bill, accountPlan, paymentMethod, chargeInfo);
        return true;
    }

    public AccountPayment recordPayment(Bill bill,
                                        AccountPlan accountPlan,
                                        AccountPaymentMethod paymentMethod,
                                        String chargeInfo) {
        // record the payment
        final AccountPayment accountPayment = accountPaymentDAO.create(new AccountPayment()
                .setType(paymentMethod.getPaymentMethodType() == promotional_credit ? AccountPaymentType.credit_applied : AccountPaymentType.payment)
                .setAccount(accountPlan.getAccount())
                .setPlan(accountPlan.getPlan())
                .setAccountPlan(accountPlan.getUuid())
                .setPaymentMethod(paymentMethod.getUuid())
                .setBill(bill.getUuid())
                .setAmount(bill.getTotal())
                .setCurrency(bill.getCurrency())
                .setStatus(AccountPaymentStatus.success)
                .setInfo(chargeInfo));

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
        final AccountPayment creditApplied = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, bill.getUuid());
        if (creditApplied != null) {
            log.warn("refund: reducing refund amount of "+refundAmount+" by credit applied "+creditApplied.getAmount());
            refundAmount -= creditApplied.getAmount();
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
