package bubble.cloud.payment.firstMonthFree;

import bubble.cloud.payment.PaymentDriverBase;
import bubble.cloud.payment.PromotionalPaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public class FirstMonthFreePaymentDriver extends PaymentDriverBase<FirstMonthPaymentConfig> implements PromotionalPaymentServiceDriver {

    private static final String FIRST_MONTH_FREE_INFO = "firstMonthFree";

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.promotional_credit; }

    @Override public boolean applyPromo(Promotion promo, Account caller) {
        // caller must not have any bills
        final int billCount = billDAO.countByAccount(caller.getUuid());
        if (billCount != 0) {
            log.warn("applyPromo: promo="+promo.getName()+", account="+caller.getName()+", account must have no Bills, found "+billCount+" bills");
            return false;
        }
        // does the caller already have one of these?
        final List<AccountPaymentMethod> existingCreditPaymentMethods = paymentMethodDAO.findByAccountAndCloud(caller.getUuid(), promo.getCloud());
        if (!empty(existingCreditPaymentMethods)) {
            log.warn("applyPromo: promo="+promo.getName()+", account="+caller.getName()+", account already has one of these promos applied");
            return true; // promo has already been applied, return true
        }
        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(caller.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(promo.getName())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));
        return true;
    }

    @Override public boolean applyReferralPromo(Promotion referralPromo, Account caller, Account referredFrom) { return false; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.promotional_credit || !paymentMethod.hasPromotion()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        if (!paymentMethod.getCloud().equals(cloud.getUuid()) || paymentMethod.deleted()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        return new PaymentValidationResult(paymentMethod);
    }

    @Override protected String charge(BubblePlan plan,
                                      AccountPlan accountPlan,
                                      AccountPaymentMethod paymentMethod,
                                      Bill bill,
                                      long chargeAmount) {
        // mark deleted so it will not be found/applied for future transactions
        log.info("charge: applying promotion: "+paymentMethod.getPromotion()+" via AccountPaymentMethod: "+paymentMethod.getUuid());
        paymentMethodDAO.update(paymentMethod.setDeleted());
        return FIRST_MONTH_FREE_INFO;
    }

    @Override protected String refund(AccountPlan accountPlan, AccountPayment payment, AccountPaymentMethod paymentMethod, Bill bill, long refundAmount) {
        reportError(getClass().getSimpleName()+": refund: cannot issue, ignoring");
        return FIRST_MONTH_FREE_INFO;
    }

}
