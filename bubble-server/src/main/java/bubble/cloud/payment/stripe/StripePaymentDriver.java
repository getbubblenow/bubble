package bubble.cloud.payment.stripe;

import bubble.cloud.payment.PaymentDriverBase;
import bubble.cloud.payment.PaymentValidationResult;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.bill.BubblePlanDAO;
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

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class StripePaymentDriver extends PaymentDriverBase<StripePaymentDriverConfig> {

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.credit; }

    private static final String PARAM_SECRET_API_KEY = "secretApiKey";

    public static final long CHARGE_CACHE_DURATION = TimeUnit.HOURS.toSeconds(24);
    private static final Object lock = new Object();

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private BillDAO billDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountPolicyDAO policyDAO;

    @Autowired private RedisService redisService;
    @Getter(lazy=true) private final RedisService chargeCache = redisService.prefixNamespace(getClass().getSimpleName());

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

    @Override public boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                                      int purchaseAmount, String currency) {
        final AccountPlan accountPlan = accountPlanDAO.findByUuid(accountPlanUuid);
        if (accountPlan == null) throw invalidEx("err.purchase.planNotFound");

        final Bill bill = billDAO.findByUuid(billUuid);
        if (bill == null) throw invalidEx("err.purchase.billNotFound");

        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentMethodUuid);
        if (paymentMethod == null) throw invalidEx("err.paymentMethod.required");

        if (!paymentMethod.getAccount().equals(accountPlan.getAccount()) || !paymentMethod.getAccount().equals(bill.getAccount())) {
            throw invalidEx("err.purchase.billNotFound");
        }

        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        if (plan == null) throw invalidEx("err.purchase.planNotFound");

        final Charge charge;
        final Map<String, Object> chargeParams = new LinkedHashMap<>();;
        final RedisService cache = getChargeCache();
        try {
            chargeParams.put("amount", purchaseAmount); // Amount in cents
            chargeParams.put("currency", currency.toLowerCase());
            chargeParams.put("customer", paymentMethod.getPaymentInfo());
            chargeParams.put("description", plan.chargeDescription());
            chargeParams.put("statement_description", plan.chargeDescription());
            final String chargeJson = json(chargeParams, COMPACT_MAPPER);
            final String cached = cache.get(billUuid);
            if (cached != null) {
                try {
                    charge = json(chargeJson, Charge.class);
                } catch (Exception e) {
                    final String msg = "purchase: error parsing cached charge: " + e;
                    log.error(msg);
                    throw invalidEx("err.purchase.cardUnknownError", msg);
                }
            } else {
                synchronized (lock) {
                    charge = Charge.create(chargeParams);
                }
            }
            if (charge.getStatus() == null) {
                final String msg = "purchase: no status returned for Charge, accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                log.error(msg);
                throw invalidEx("err.purchase.cardUnknownError", msg);
            } else {
                final String msg;
                switch (charge.getStatus()) {
                    case "succeeded":
                        log.info("purchase: charge successful: chargeId="+charge.getId()+", charge="+chargeJson);
                        cache.set(billUuid, json(charge), "EX", CHARGE_CACHE_DURATION);
                        return true;

                    case "pending":
                        msg = "purchase: status='pending' (expected 'succeeded'), chargeId="+charge.getId()+", accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargePendingError", msg);

                    default:
                        msg = "purchase: status='"+charge.getStatus()+"' (expected 'succeeded'), chargeId="+charge.getId()+", accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid;
                        log.error(msg);
                        throw invalidEx("err.purchase.chargeFailedError", msg);
                }
            }

        } catch (CardException e) {
            // The card has been declined
            final String msg = "purchase: CardException for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", declineCode="+e.getDeclineCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardError", msg);

        } catch (StripeException e) {
            final String msg = "purchase: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": requestId=" + e.getRequestId() + ", code="+e.getCode()+", error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardProcessingError", msg);

        } catch (Exception e) {
            final String msg = "purchase: "+e.getClass().getSimpleName()+" for accountPlan=" + accountPlanUuid + " with paymentMethod=" + paymentMethodUuid + " and bill=" + billUuid + ": error=" + e.toString();
            log.error(msg);
            throw invalidEx("err.purchase.cardUnknownError", msg);
        }
    }

    @Override public boolean refund(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                                    int refundAmount, String currency) {
        log.error("refund: not yet supported: accountPlanUuid="+accountPlanUuid+", paymentMethodUuid="+paymentMethodUuid+", billUuid="+billUuid);
        return false;
    }

}
