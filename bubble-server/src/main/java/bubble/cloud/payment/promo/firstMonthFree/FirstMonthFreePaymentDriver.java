package bubble.cloud.payment.promo.firstMonthFree;

import bubble.cloud.payment.promo.PromotionPaymentConfig;
import bubble.cloud.payment.promo.PromotionalPaymentDriverBase;
import bubble.cloud.payment.promo.PromotionalPaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.bill.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class FirstMonthFreePaymentDriver extends PromotionalPaymentDriverBase<PromotionPaymentConfig> {

    @Override public boolean addPromoToAccount(Promotion promo, Account caller) {
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

    @Override public boolean canUseNow(Bill bill,
                                       Promotion promo,
                                       PromotionalPaymentServiceDriver promoDriver,
                                       Map<Promotion, AccountPaymentMethod> promos,
                                       Set<Promotion> usable,
                                       AccountPlan accountPlan,
                                       AccountPaymentMethod paymentMethod) {
        // must have exactly one bill, this bill
        final List<Bill> bills = billDAO.findByAccount(accountPlan.getAccount());
        if (bills.size() != 1 || !bills.get(0).getUuid().equals(bill.getUuid())) {
            return false;
        }

        // must not have used this promotion before
        final List<AccountPaymentMethod> existingFirstMonthFree = paymentMethodDAO.findByAccountAndCloud(accountPlan.getAccount(), promo.getCloud());
        if (existingFirstMonthFree.size() != 1 || !existingFirstMonthFree.get(0).getPromotion().equals(promo.getUuid())) {
            return false;
        }

        return super.canUseNow(bill, promo, promoDriver, promos, usable, accountPlan, paymentMethod);
    }

}
