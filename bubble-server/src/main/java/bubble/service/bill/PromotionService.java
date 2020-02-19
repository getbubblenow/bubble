package bubble.service.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.payment.promo.PromotionalPaymentServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.ReferralCodeDAO;
import bubble.dao.bill.AccountPaymentDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import bubble.model.bill.*;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static bubble.model.bill.AccountPayment.totalPayments;
import static bubble.model.bill.Promotion.SORT_PAYMENT_METHOD_CTIME;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class PromotionService {

    @Autowired private PromotionDAO promotionDAO;
    @Autowired private ReferralCodeDAO referralCodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired protected AccountPaymentDAO accountPaymentDAO;
    @Autowired protected AccountPaymentMethodDAO accountPaymentMethodDAO;
    @Autowired private BubbleConfiguration configuration;

    public void applyPromotions(Account account, String code, String currency) {
        if (configuration.promoCodesDisabled()) {
            log.info("applyPromotions: promotions disabled, not applying any");
            return;
        }
        // apply promo code (or default) promotion
        final Set<Promotion> promos = new TreeSet<>();
        ReferralCode referralCode = null;
        if (!empty(code)) {
            Promotion promo = promotionDAO.findEnabledAndActiveWithCode(code, currency);
            if (promo == null) {
                // check referral codes
                // it might be a referral code
                referralCode = referralCodeDAO.findByName(code);
                if (referralCode != null && !referralCode.claimed()) {
                    // is there a referral promotion we can use?
                    for (Promotion p : promotionDAO.findEnabledAndActiveWithReferral(currency)) {
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
        for (Promotion p : promotionDAO.findEnabledAndActiveWithNoCode(currency)) {
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
                        if (!promoPaymentDriver.addReferralPromoToAccount(p, account, referer, referralCode)) {
                            throw invalidEx("err.promoCode.notApplied");
                        }
                    } else {
                        if (!promoPaymentDriver.addPromoToAccount(p, account)) {
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

    public ValidationResult validatePromotions(String code, String currency) {
        if (!empty(code)) {
            if (configuration.promoCodesDisabled()) {
                return new ValidationResult("err.promoCode.disabled");
            }
            Promotion promo = promotionDAO.findEnabledAndActiveWithCode(code, currency);
            if (promo == null) {
                // it might be a referral code
                final ReferralCode referralCode = referralCodeDAO.findByName(code);
                if (referralCode != null && !referralCode.claimed()) {
                    final Account referer = accountDAO.findById(referralCode.getAccountUuid());
                    if (referer == null || referer.deleted()) return new ValidationResult("err.promoCode.notFound");

                    // is there a referral promotion we can use?
                    for (Promotion p : promotionDAO.findEnabledAndActiveWithReferral(currency)) {
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

    public long usePromotions(BubblePlan plan,
                              AccountPlan accountPlan,
                              Bill bill,
                              AccountPaymentMethod paymentMethod,
                              PaymentServiceDriver paymentDriver,
                              List<Promotion> promos,
                              long chargeAmount) {
        if (configuration.promoCodesDisabled()) {
            log.warn("usePromotions: promo codes are disabled, not using");
            return chargeAmount;
        }
        if (chargeAmount <= 0) {
            log.error("usePromotions: chargeAmount <= 0 : "+chargeAmount);
            return chargeAmount;
        }
        if (paymentDriver instanceof PromotionalPaymentServiceDriver) {
            log.warn("usePromotions: must be used with another payment driver, not a "+paymentDriver.getClass().getName());
            return chargeAmount;
        }

        promos.sort(Promotion::compareTo);

        // find the payment cloud associated with the promo, defer to that
        final String accountPlanUuid = accountPlan.getUuid();
        final Set<Promotion> used = new HashSet<>();
        for (Promotion promo : promos) {
            final AccountPaymentMethod apm = promo.getPaymentMethod();
            final CloudService promoCloud = cloudDAO.findByUuid(promo.getCloud());
            final String prefix = getClass().getSimpleName()+": ";
            if (promoCloud == null) {
                reportError(prefix+"purchase: cloud "+promo.getCloud()+" not found for promotion "+promo.getName());
                continue;
            }
            if (promoCloud.getType() != CloudServiceType.payment) {
                reportError(prefix+"purchase: cloud "+promo.getCloud()+" for promotion "+promo.getName()+" has wrong type (expected 'payment'): "+promoCloud.getType());
                continue;
            }
            if (!promo.getCurrency().equals(plan.getCurrency())) {
                reportError(prefix+"purchase: promotion "+promo.getName()+" has wrong currency (expected "+plan.getCurrency()+" for plan "+plan.getName()+"): "+promoCloud.getType());
                continue;
            }
            log.info("purchase: using Promotion: "+promo.getName());
            try {
                final PaymentServiceDriver promoPaymentDriver = promoCloud.getPaymentDriver(configuration);
                final PromotionalPaymentServiceDriver promoDriver = (PromotionalPaymentServiceDriver) promoPaymentDriver;
                if (!promoDriver.canUseNow(bill, promo, promoDriver, promos, used, accountPlan, paymentMethod)) {
                    log.warn("purchase: Promotion "+promo.getName()+" cannot currently be used for accountPlan "+ accountPlanUuid);
                    continue;
                }
                promoDriver.purchase(accountPlanUuid, apm.getUuid(), bill.getUuid());
                used.add(promo);

                // verify AccountPayments exists for new payment with promo
                final List<AccountPayment> creditsApplied = accountPaymentDAO.findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(accountPlan.getAccount(), accountPlanUuid, bill.getUuid());
                final List<AccountPayment> creditsByThisPromo = creditsApplied.stream()
                        .filter(c -> c.getPaymentMethod().equals(apm.getUuid()))
                        .collect(Collectors.toList());
                if (empty(creditsByThisPromo)) {
                    log.warn("purchase: applying promotion did not result in an AccountPayment to Bill "+bill.getUuid());
                    continue;
                }
                if (creditsByThisPromo.size() > 1) {
                    reportError("purchase: multiple credits applied by promo: "+promo.getName()+": "+StringUtil.toString(creditsByThisPromo));
                }

                if (paymentDriver.getPaymentMethodType().requiresAuth() && !paymentDriver.cancelAuthorization(plan, accountPlanUuid, paymentMethod)) {
                    log.warn("purchase: error cancelling authorization for accountPlanUuid=" + accountPlanUuid + ", paymentMethod=" + paymentMethod.getUuid());
                }
                if (totalPayments(creditsApplied) >= bill.getTotal()) {
                    log.info("purchase: applying promotion paid full bill, canceled current payment authorization");
                    return 0;
                } else {
                    final int promoCredits = totalPayments(creditsByThisPromo);
                    log.info("purchase: promotion applied credits of " + promoCredits + " on a bill of " + bill.getTotal() + ", using current paymentMethod to pay the remainder; reauthorizing now...");
                    chargeAmount -= promoCredits;
                    if (paymentDriver.getPaymentMethodType().requiresAuth() && !paymentDriver.authorize(plan, accountPlanUuid, bill.getUuid(), paymentMethod)) {
                        reportError(prefix+"purchase: after applying credit and cancelling previous charge authorization, new charge authorization failed");
                        continue;
                    }
                    if (chargeAmount <= 0) {
                        log.info("purchase: chargeAmount < 0 ("+chargeAmount+") returning 0");
                        return 0;
                    }
                }
            } catch (Exception e) {
                reportError(prefix+"purchase: error applying promotion "+promo.getName()+" to accountPlan "+accountPlanUuid+": "+shortError(e));
                continue;
            }
        }
        return chargeAmount;
    }

    public List<Promotion> listPromosForAccount(String accountUuid) {
        final List<Promotion> promos = new ArrayList<>();
        final List<AccountPaymentMethod> apmList = accountPaymentMethodDAO.findByAccountAndPromoAndNotDeleted(accountUuid);
        for (AccountPaymentMethod apm : apmList) {
            final Promotion promo = promotionDAO.findByUuid(apm.getPromotion());
            if (promo == null) {
                log.warn("listPromos: promo "+apm.getPromotion()+" not found for apm="+apm.getUuid());
                continue;
            }
            if (!promo.enabled() && !promo.getVisible()) {
                log.warn("listPromos: promo "+apm.getPromotion()+" is not enabled and not admin-assigned, apm="+apm.getUuid());
                continue;
            }
            promos.add(promo.setPaymentMethod(apm));
        }
        promos.sort(SORT_PAYMENT_METHOD_CTIME);
        return promos;
    }

    public List<Promotion> adminAddPromotion(Account account, Promotion request) {
        final Promotion promotion = request.hasUuid()
                ? promotionDAO.findByUuid(request.getUuid())
                : promotionDAO.findByName(request.getName());
        if (promotion == null) throw notFoundEx(json(request));
        final var promoDriver = (PromotionalPaymentServiceDriver) cloudDAO.findByUuid(promotion.getCloud()).getPaymentDriver(configuration);
        promoDriver.adminAddPromoToAccount(promotion, account);
        return listPromosForAccount(account.getUuid());
    }

    public List<Promotion> adminRemovePromotion(Account account, Promotion request) {

        final Promotion promotion = request.hasUuid()
                ? promotionDAO.findByUuid(request.getUuid())
                : promotionDAO.findByName(request.getName());
        if (promotion == null) throw notFoundEx(json(request));

        final AccountPaymentMethod paymentMethod = accountPaymentMethodDAO.findByAccount(account.getUuid()).stream()
                .filter(apm -> apm.hasPromotion() && apm.getPromotion().equals(promotion.getUuid()))
                .findFirst().orElse(null);
        if (paymentMethod == null) throw notFoundEx(promotion.getName());

        accountPaymentMethodDAO.update(paymentMethod.setDeleted());

        return listPromosForAccount(account.getUuid());
    }
}
