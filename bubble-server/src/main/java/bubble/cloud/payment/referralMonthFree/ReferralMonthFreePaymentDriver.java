package bubble.cloud.payment.referralMonthFree;

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
public class ReferralMonthFreePaymentDriver extends PaymentDriverBase<ReferralMonthPaymentConfig> implements PromotionalPaymentServiceDriver {

    private static final String REFERRAL_MONTH_FREE_INFO = "referralMonthFree";

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.promotional_credit; }

    @Override public boolean applyPromo(Promotion promo, Account caller) { return false; }

    @Override public boolean applyReferralPromo(Promotion promo, Account caller, Account referredFrom) {
        // caller must not have any bills
        final int billCount = billDAO.countByAccount(caller.getUuid());
        if (billCount != 0) {
            log.warn("applyReferralPromo: promo="+promo.getName()+", account="+caller.getName()+", account must have no Bills, found "+billCount+" bills");
            return false;
        }

        // check AccountPaymentMethods for referredFrom
        final List<AccountPaymentMethod> referredFromCreditPaymentMethods = paymentMethodDAO.findByAccountAndCloud(referredFrom.getUuid(), promo.getCloud());

        // It's OK for the referredFrom user to have many of these, as long as there is not one for this user
        for (AccountPaymentMethod apm : referredFromCreditPaymentMethods) {
            if (apm.getPaymentInfo().equals(caller.getUuid())) {
                log.error("applyReferralPromo: promo="+promo.getName()+", account="+caller.getName()+", referredFrom="+referredFrom.getName()+" has already referred this caller");
                return false;
            }
        }

        // does the caller already have one of these?
        final List<AccountPaymentMethod> existingCreditPaymentMethods = paymentMethodDAO.findByAccountAndCloud(caller.getUuid(), promo.getCloud());
        if (!empty(existingCreditPaymentMethods)) {
            log.warn("applyReferralPromo: promo="+promo.getName()+", account="+caller.getName()+", account already has one of these promos applied");
            return true; // promo has already been applied, return true
        }

        // create new APMs for caller and referredFrom
        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(caller.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(referredFrom.getUuid())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));

        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(referredFrom.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(caller.getUuid())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));

        return true;
    }

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
        return REFERRAL_MONTH_FREE_INFO;
    }

    @Override protected String refund(AccountPlan accountPlan, AccountPayment payment, AccountPaymentMethod paymentMethod, Bill bill, long refundAmount) {
        reportError(getClass().getSimpleName()+": refund: cannot issue, ignoring");
        return REFERRAL_MONTH_FREE_INFO;
    }

}
