package bubble.cloud.payment.stripe;

import bubble.cloud.payment.PaymentDriverBase;
import bubble.notify.payment.PaymentValidationResult;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.AccountPolicy;
import bubble.model.bill.*;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Boolean.TRUE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class StripePaymentDriver extends PaymentDriverBase<StripePaymentDriverConfig> {

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.credit; }

    private static final String PARAM_SECRET_API_KEY = "secretApiKey";

    public static final long AUTH_CACHE_DURATION = TimeUnit.DAYS.toSeconds(7);
    public static final long CHARGE_CACHE_DURATION = TimeUnit.HOURS.toSeconds(24);
    private static final Object lock = new Object();

    @Autowired private AccountPolicyDAO policyDAO;

    @Autowired private RedisService redisService;
    @Getter(lazy=true) private final RedisService authCache = redisService.prefixNamespace(getClass().getSimpleName()+"_auth");
    @Getter(lazy=true) private final RedisService chargeCache = redisService.prefixNamespace(getClass().getSimpleName()+"_charge");

    private static final AtomicReference<String> setupDone = new AtomicReference<>(null);

    @Override public void postSetup() {
        if (setupDone.get() == null) {
            synchronized (setupDone) {
                if (setupDone.get() == null) {
                    final String apiKey = getCredentials().getParam(PARAM_SECRET_API_KEY);
                    if (empty(apiKey)) die("postSetup: "+PARAM_SECRET_API_KEY+" not found in credentials");
                    Stripe.apiKey = apiKey;
                    setupDone.set(cloud.getUuid());
                } else {
                    log.info("postSetup: already set up");
                }
            }
        } else {
            log.info("postSetup: already set up");
        }
        if (!setupDone.get().equals(cloud.getUuid())) {
            die("postSetup: cannot re-initialize with another API key (only one "+getClass().getSimpleName()+" is supported)");
        }
    }

    @Override public PaymentValidationResult validate(AccountPaymentMethod accountPaymentMethod) {
        final String info = accountPaymentMethod.getPaymentInfo();
        if (info == null) return new PaymentValidationResult("err.paymentInfo.required");

        if (!info.trim().startsWith("cus_")) {
            // not a customer token, is it a raw card token?
            if (!info.trim().startsWith("tok_")) return new PaymentValidationResult("err.paymentInfo.invalid");

            // it's a card token, convert this into a customer
            final AccountPolicy policy = policyDAO.findSingleByAccount(accountPaymentMethod.getAccount());
            if (policy == null) return new PaymentValidationResult("err.paymentInfo.emailRequired");

            final String email = policy.getFirstVerifiedEmail();
            if (email == null && policy.getFirstEmail() != null) {
                return new PaymentValidationResult("err.paymentInfo.verifiedEmailRequired");
            }

            final Map<String, Object> customerParams = new HashMap<>();
            customerParams.put("source", info.trim());
            customerParams.put("email", email);
            final Customer customer;
            final String last4;
            try {
                customer = Customer.create(customerParams);
                last4 = ((Card) customer.getSources().getData().get(0)).getLast4();
            } catch (StripeException e) {
                log.error("validate: error calling Customer.create: "+e);
                return new PaymentValidationResult("err.paymentInfo.processingError");
            }
            if (empty(customer.getId())) {
                log.error("validate: Customer.getId() returned null or empty");
                return new PaymentValidationResult("err.paymentInfo.processingError");
            }

            // save customer and last4
            accountPaymentMethod.setPaymentInfo(customer.getId());
            accountPaymentMethod.setMaskedPaymentInfo("XXXX-".repeat(3) + last4);

        } else {
            // retrieve the existing customer, verify valid
            try {
                Customer.retrieve(info);
            } catch (StripeException e) {
                log.error("validate: error calling Customer.retrieve: "+e);
                return new PaymentValidationResult("err.paymentInfo.processingError");
            }
        }
        return new PaymentValidationResult(accountPaymentMethod);
    }

    public String getAuthCacheKey(String accountPlanUuid, String paymentMethodUuid) {
        return accountPlanUuid+":"+paymentMethodUuid;
    }

    @Override public boolean authorize(BubblePlan plan,
                                       AccountPlan accountPlan,
                                       AccountPaymentMethod paymentMethod) {
        final String accountPlanUuid = accountPlan.getUuid();
        final String paymentMethodUuid = paymentMethod.getUuid();

        final Charge charge;
        final Map<String, Object> chargeParams = new LinkedHashMap<>();;
        final RedisService authCache = getAuthCache();
        try {
            chargeParams.put("amount", plan.getPrice()); // Amount in cents
            chargeParams.put("currency", plan.getCurrency().toLowerCase());
            chargeParams.put("customer", paymentMethod.getPaymentInfo());
            chargeParams.put("description", plan.chargeDescription());
            chargeParams.put("statement_description", plan.chargeDescription());
            chargeParams.put("capture", false);
            final String chargeJson = json(chargeParams, COMPACT_MAPPER);
            final String authCacheKey = getAuthCacheKey(accountPlanUuid, paymentMethodUuid);
            final String chargeId = authCache.get(authCacheKey);
            if (chargeId != null) {
                log.warn("authorize: already authorized: "+authCacheKey);
                return true;
            }
            synchronized (lock) {
                charge = Charge.create(chargeParams);
            }
            if (charge.getStatus() == null) {
                final String msg = "authorize: no status returned for Charge, accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid;
                log.error(msg);
                throw invalidEx("err.purchase.cardUnknownError", msg);
            } else {
                final String msg;
                switch (charge.getStatus()) {
                    case "succeeded":
                        if (charge.getReview() != null) {
                            log.info("authorize: successful but is under review: charge=" + chargeJson);
                        } else {
                            log.info("authorize: successful: charge=" + chargeJson);
                        }
                        authCache.set(authCacheKey, charge.getId(), "EX", AUTH_CACHE_DURATION);
                        return true;

                    case "pending":
                        msg = "authorize: status='pending' (expected 'succeeded'), accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargePendingError", msg);

                    default:
                        msg = "authorize: status='" + charge.getStatus() + "' (expected 'succeeded'), accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargeFailedError", msg);
                }
            }
        } catch (CardException e) {
            // The card has been declined
            final String msg = "authorize: CardException for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", declineCode="+e.getDeclineCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardError", msg);

        } catch (StripeException e) {
            final String msg = "authorize: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardProcessingError", msg);

        } catch (Exception e) {
            final String msg = "authorize: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + ": error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardUnknownError", msg);
        }
    }

    @Override protected void charge(BubblePlan plan,
                                    AccountPlan accountPlan,
                                    AccountPaymentMethod paymentMethod,
                                    AccountPlanPaymentMethod planPaymentMethod,
                                    Bill bill) {
        final String accountPlanUuid = accountPlan.getUuid();
        final String paymentMethodUuid = paymentMethod.getUuid();
        final String billUuid = bill.getUuid();

        final Charge charge;
        final RedisService authCache = getAuthCache();
        final RedisService chargeCache = getChargeCache();

        final String authCacheKey = getAuthCacheKey(accountPlanUuid, paymentMethodUuid);
        try {
            final String charged = chargeCache.get(billUuid);
            if (charged != null) {
                // already charged, nothing to do
                log.info("charge: already charged: "+charged);
                return;
            }

            final String chargeId = authCache.get(authCacheKey);
            if (chargeId == null) throw invalidEx("err.purchase.authNotFound");

            try {
                charge = Charge.retrieve(chargeId);
            } catch (Exception e) {
                final String msg = "charge: error retrieving charge: " + e;
                log.error(msg);
                throw invalidEx("err.purchase.cardUnknownError", msg);
            }
            if (charge.getReview() != null) {
                final String msg = "charge: charge "+chargeId+" still under review: " + charge.getReview();
                log.error(msg);
                throw invalidEx("err.purchase.underReview", msg);
            }
            final Charge captured;
            synchronized (lock) {
                captured = charge.capture();
            }
            if (captured.getStatus() == null) {
                final String msg = "charge: no status returned for Charge, accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                log.error(msg);
                throw invalidEx("err.purchase.cardUnknownError", msg);
            } else {
                final String msg;
                switch (captured.getStatus()) {
                    case "succeeded":
                        log.info("charge: charge successful: "+authCacheKey);
                        chargeCache.set(billUuid, TRUE.toString(), "EX", CHARGE_CACHE_DURATION);
                        return;

                    case "pending":
                        msg = "charge: status='pending' (expected 'succeeded'), accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargePendingError", msg);

                    default:
                        msg = "charge: status='"+charge.getStatus()+"' (expected 'succeeded'), accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargeFailedError", msg);
                }
            }

        } catch (CardException e) {
            // The card has been declined
            final String msg = "charge: CardException for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", declineCode="+e.getDeclineCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardError", msg);

        } catch (StripeException e) {
            final String msg = "charge: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardProcessingError", msg);

        } catch (Exception e) {
            final String msg = "charge: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardUnknownError", msg);
        }
    }

}
