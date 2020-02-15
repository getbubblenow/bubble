package bubble.service.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.payment.PromotionalPaymentServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.ReferralCodeDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import bubble.model.bill.PaymentMethodType;
import bubble.model.bill.Promotion;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TreeSet;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class PromotionService {

    @Autowired private PromotionDAO promotionDAO;
    @Autowired private ReferralCodeDAO referralCodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleConfiguration configuration;

    public void applyPromotions(Account account, String code) {
        // apply promo code (or default) promotion
        final Set<Promotion> promos = new TreeSet<>();
        ReferralCode referralCode = null;
        if (!empty(code)) {
            Promotion promo = promotionDAO.findEnabledAndActiveWithCode(code);
            if (promo == null) {
                // check referral codes
                // it might be a referral code
                referralCode = referralCodeDAO.findByName(code);
                if (referralCode != null && !referralCode.used()) {
                    // is there a referral promotion we can use?
                    for (Promotion p : promotionDAO.findEnabledAndActiveWithReferral()) {
                        promos.add(p);
                        break;
                    }
                }
            } else {
                promos.add(promo);
            }
            if (promos.isEmpty()) throw invalidEx("err.promoCode.notFound");
        }

        // everyone gets the highest-priority default promotion, if there are any enabled and active
        for (Promotion p : promotionDAO.findEnabledAndActiveWithNoCode()) {
            promos.add(p);
            break;
        }

        if (promos.isEmpty()) return;  // nothing to do

        for (Promotion p : promos) {
            final CloudService promoCloud = cloudDAO.findByUuid(p.getCloud());
            if (promoCloud == null || promoCloud.getType() != CloudServiceType.payment) {
                throw invalidEx("err.promoCode.configurationError");
            } else {
                final PaymentServiceDriver promoDriver = promoCloud.getPaymentDriver(configuration);
                if (promoDriver.getPaymentMethodType() != PaymentMethodType.promotional_credit
                        || !(promoDriver instanceof PromotionalPaymentServiceDriver)) {
                    throw invalidEx("err.promoCode.configurationError");
                } else {
                    final PromotionalPaymentServiceDriver promoPaymentDriver = (PromotionalPaymentServiceDriver) promoDriver;
                    if (p.referral()) {
                        if (referralCode == null) throw invalidEx("err.promoCode.notFound");
                        final Account referer = accountDAO.findById(referralCode.getAccountUuid());
                        if (referer == null || referer.deleted()) throw invalidEx("err.promoCode.notFound");
                        if (!promoPaymentDriver.applyReferralPromo(p, account, referer)) {
                            throw invalidEx("err.promoCode.notApplied");
                        }
                    } else {
                        if (!promoPaymentDriver.applyPromo(p, account)) {
                            if (!empty(code)) {
                                throw invalidEx("err.promoCode.notApplied");
                            } else {
                                log.warn("setReferences: promo not applied: " + p.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    public ValidationResult validatePromotions(String code) {
        if (!empty(code)) {
            Promotion promo = promotionDAO.findEnabledAndActiveWithCode(code);
            if (promo == null) {
                // it might be a referral code
                final ReferralCode referralCode = referralCodeDAO.findByName(code);
                if (referralCode != null && !referralCode.used()) {
                    final Account referer = accountDAO.findById(referralCode.getAccountUuid());
                    if (referer == null || referer.deleted()) return new ValidationResult("err.promoCode.notFound");

                    // is there a referral promotion we can use?
                    for (Promotion p : promotionDAO.findEnabledAndActiveWithReferral()) {
                        // todo: add JS check?
                        promo = p;
                        break;
                    }
                }
                if (promo == null) return new ValidationResult("err.promoCode.notFound");
            }

            final CloudService promoCloud = cloudDAO.findByUuid(promo.getCloud());
            if (promoCloud == null || promoCloud.getType() != CloudServiceType.payment) {
                return new ValidationResult("err.promoCode.configurationError");
            }
            // sanity check the driver
            try {
                final PaymentServiceDriver driver = promoCloud.getPaymentDriver(configuration);
                final PromotionalPaymentServiceDriver promoDriver = (PromotionalPaymentServiceDriver) driver;
            } catch (Exception e) {
                log.error("validatePromotions: error applying referral promo: "+shortError(e));
                return new ValidationResult("err.promoCode.configurationError");
            }
        }
        return null;
    }
}
