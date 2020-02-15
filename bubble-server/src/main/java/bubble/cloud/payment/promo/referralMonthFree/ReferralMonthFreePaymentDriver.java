package bubble.cloud.payment.promo.referralMonthFree;

import bubble.cloud.payment.promo.PromotionPaymentConfig;
import bubble.cloud.payment.promo.PromotionalPaymentDriverBase;
import bubble.cloud.payment.promo.PromotionalPaymentServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.ReferralCodeDAO;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import bubble.model.bill.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public class ReferralMonthFreePaymentDriver extends PromotionalPaymentDriverBase<PromotionPaymentConfig> {

    @Autowired private ReferralCodeDAO referralCodeDAO;
    @Autowired private AccountDAO accountDAO;

    @Override public boolean addReferralPromoToAccount(Promotion promo,
                                                       Account caller,
                                                       Account referredFrom,
                                                       ReferralCode referralCode) {
        // sanity check
        if (!promo.enabled()) {
            log.warn("applyReferralPromo: promo="+promo.getName()+" is not enabled");
            return false;
        }

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

        // sanity check before using
        final ReferralCode ref = referralCodeDAO.findByUuid(referralCode.getUuid());
        if (ref == null) {
            log.warn("applyReferralPromo: promo="+promo.getName()+", account="+caller.getName()+", referralCode "+referralCode.getUuid()+" was not found");
            return false;
        }
        if (ref.claimed()) {
            log.warn("applyReferralPromo: promo="+promo.getName()+", account="+caller.getName()+", referralCode "+referralCode.getUuid()+" has already been claimed by "+ref.getClaimedByUuid());
            return false;
        }

        // mark referral code used
        referralCodeDAO.update(ref.setClaimedBy(caller.getUuid()).setClaimedByUuid(caller.getUuid()));

        // create new APMs for caller and referredFrom
        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(caller.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(referralCode.getUuid())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));

        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(referredFrom.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(referralCode.getUuid())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));

        return true;
    }

    @Override public boolean canUseNow(Bill bill,
                                       Promotion promo,
                                       PromotionalPaymentServiceDriver promoDriver,
                                       List<Promotion> promos,
                                       Set<Promotion> usable,
                                       AccountPlan accountPlan,
                                       AccountPaymentMethod paymentMethod) {
        final String prefix = getClass().getSimpleName() + ".canUseNow: ";
        try {
            final AccountPaymentMethod promoPaymentMethod = promo.getPaymentMethod();
            final String referralCodeUuid = promoPaymentMethod.getPaymentInfo();
            final ReferralCode referralCode = referralCodeDAO.findByUuid(referralCodeUuid);
            if (referralCode == null) {
                reportError(prefix+"referralCode not found: "+referralCodeUuid+" for promoPaymentMethod="+promoPaymentMethod.getUuid()+", promo="+promo.getName());
                return false;
            }

            // both users must still exist
            final Account referringUser = accountDAO.findByUuid(referralCode.getAccount());
            if (referringUser == null || referringUser.deleted()) {
                reportError(prefix+"referring user not found or deleted for referralCode: "+referralCodeUuid+" for promoPaymentMethod="+promoPaymentMethod.getUuid()+", promo="+promo.getName());
                return false;
            }
            final Account referredUser = accountDAO.findByUuid(referralCode.getClaimedBy());
            if (referredUser == null || referredUser.deleted()) {
                reportError(prefix+"referred user not found or deleted for referralCode: "+referralCodeUuid+" for promoPaymentMethod="+promoPaymentMethod.getUuid()+", promo="+promo.getName());
                return false;
            }

            // for referring user: can't use credit until the referred user has at least paid for 1 bill
            if (accountPlan.getAccount().equals(referringUser.getUuid())) {
                if (empty(accountPaymentDAO.findByAccountAndPaymentSuccess(referredUser.getUuid()))) {
                    // have to wait until the other party has paid at least the min value on the promotion
                    return false;
                }
                // referred user has a bill
                return super.canUseNow(bill, promo, promoDriver, promos, usable, accountPlan, paymentMethod);
            } else {
                // they are the referred user -- claim their bonus
                return super.canUseNow(bill, promo, promoDriver, promos, usable, accountPlan, paymentMethod);
            }

        } catch (Exception e) {
            reportError(prefix + shortError(e), e);
            return false;
        }
    }

}
