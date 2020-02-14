package bubble.cloud.payment.code;

import bubble.cloud.payment.DefaultPaymentDriverConfig;
import bubble.cloud.payment.PaymentDriverBase;
import bubble.notify.payment.PaymentValidationResult;
import bubble.dao.bill.*;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.dao.cloud.CloudServiceDataDAO;
import bubble.model.bill.*;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.CloudServiceData;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.bill.AccountPaymentMethod.DEFAULT_MASKED_PAYMENT_INFO;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class CodePaymentDriver extends PaymentDriverBase<DefaultPaymentDriverConfig> {

    @Autowired private CloudServiceDataDAO dataDAO;

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.code; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.code) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        final ValidationResult result = new ValidationResult();
        final CodePaymentToken cpToken = readToken(configuration, paymentMethod, result, cloud);
        if (result.isInvalid()) return new PaymentValidationResult(result.getViolationBeans());
        if (cpToken == null) return new PaymentValidationResult("err.paymentInfo");
        return new PaymentValidationResult(paymentMethod.setMaskedPaymentInfo(mask(cpToken.getToken())));
    }

    @Override public synchronized PaymentValidationResult claim(AccountPaymentMethod accountPaymentMethod) {
        final CloudServiceDAO cloudDAO = configuration.getBean(CloudServiceDAO.class);
        final CloudService paymentCloud = cloudDAO.findByUuid(accountPaymentMethod.getCloud());
        final CodePaymentToken cpToken = readToken(configuration, accountPaymentMethod, new ValidationResult(), paymentCloud);
        if (cpToken == null) return new PaymentValidationResult("err.purchase.tokenNotFound");

        if (cpToken.hasAccount()) {
            if (!cpToken.getAccount().equals(accountPaymentMethod.getAccount())) {
                // already claimed
                return new PaymentValidationResult("err.purchase.tokenUsed");
            }
        } else {
            final CloudServiceDataDAO dataDAO = configuration.getBean(CloudServiceDataDAO.class);
            cpToken.setAccount(accountPaymentMethod.getAccount());
            dataDAO.update(cpToken.getCloudServiceData().setData(json(cpToken)));
        }
        return new PaymentValidationResult(accountPaymentMethod);
    }

    @Override public synchronized PaymentValidationResult claim(AccountPlan accountPlan) {
        final CloudServiceDAO cloudDAO = configuration.getBean(CloudServiceDAO.class);
        final AccountPaymentMethodDAO paymentMethodDAO = configuration.getBean(AccountPaymentMethodDAO.class);
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());
        final CloudService paymentCloud = cloudDAO.findByUuid(paymentMethod.getCloud());
        final CodePaymentToken cpToken = readToken(configuration, paymentMethod, new ValidationResult(), paymentCloud);
        if (cpToken == null) return new PaymentValidationResult("err.purchase.tokenNotFound");

        if (cpToken.hasAccount()) {
            if (!cpToken.getAccount().equals(accountPlan.getAccount())) {
                // already claimed
                return new PaymentValidationResult("err.purchase.tokenUsed");
            }
        }
        if (cpToken.hasAccountPlan()) {
            if (!cpToken.getAccountPlan().equals(accountPlan.getUuid())) {
                return new PaymentValidationResult("err.purchase.tokenUsed");
            }
        } else {
            final CloudServiceDataDAO dataDAO = configuration.getBean(CloudServiceDataDAO.class);
            cpToken.setAccount(accountPlan.getAccount());
            cpToken.setAccountPlan(accountPlan.getUuid());
            dataDAO.update(cpToken.getCloudServiceData().setData(json(cpToken)));
        }
        return new PaymentValidationResult(paymentMethod);
    }

    public static final int MAX_UNMASKED_LENGTH = 4;
    private String mask(String info) {
        if (info == null || info.length() < MAX_UNMASKED_LENGTH) return DEFAULT_MASKED_PAYMENT_INFO;
        info = info.replaceAll("\\s+", "");
        final int maskLength = info.length() - MAX_UNMASKED_LENGTH;
        return "X".repeat(12) + info.substring(maskLength);
    }

    public static CodePaymentToken readToken(BubbleConfiguration configuration,
                                             AccountPaymentMethod accountPaymentMethod,
                                             ValidationResult result,
                                             CloudService paymentCloud) {
        if (paymentCloud == null) {
            result.addViolation("err.paymentService.required");
        } else {
            final CloudServiceDataDAO dataDAO = configuration.getBean(CloudServiceDataDAO.class);
            CloudServiceData csData = dataDAO.findByCloudAndKey(paymentCloud.getUuid(), accountPaymentMethod.getPaymentInfo());
            if (csData == null) {
                // try delegated service, if we can find it
                if (paymentCloud.delegated()) {
                    final CloudServiceDAO cloudDAO = configuration.getBean(CloudServiceDAO.class);
                    paymentCloud = cloudDAO.findByUuid(paymentCloud.getDelegated());
                    if (paymentCloud == null) {
                        result.addViolation("err.paymentService.required");
                    } else {
                        csData = dataDAO.findByCloudAndKey(paymentCloud.getUuid(), accountPaymentMethod.getPaymentInfo());
                    }
                }
            }
            if (csData == null) {
                result.addViolation("err.purchase.tokenNotFound");
            } else {
                CodePaymentToken cpToken = null;
                try {
                    cpToken = json(csData.getData(), CodePaymentToken.class);
                } catch (Exception e) {
                    result.addViolation("err.purchase.tokenInvalid");
                }
                if (cpToken != null) {
                    if (!cpToken.getToken().equals(accountPaymentMethod.getPaymentInfo())) {
                        result.addViolation("err.purchase.tokenMismatch");
                    } else {
                        if (cpToken.expired()) {
                            result.addViolation("err.purchase.tokenExpired");
                        } else {
                            return cpToken.setCloudServiceData(csData);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override protected String charge(BubblePlan plan,
                                      AccountPlan accountPlan,
                                      AccountPaymentMethod paymentMethod,
                                      Bill bill,
                                      long chargeAmount) {
        // is the token valid?
        final CloudServiceData csData = dataDAO.findByCloudAndKey(cloud.getUuid(), paymentMethod.getPaymentInfo());
        if (csData == null) throw invalidEx("err.purchase.tokenNotFound");

        final CodePaymentToken cpToken;
        try {
            cpToken = json(csData.getData(), CodePaymentToken.class);
        } catch (Exception e) {
            throw invalidEx("err.purchase.tokenInvalid");
        }
        if (cpToken.expired()) throw invalidEx("err.purchase.tokenExpired");
        if (!cpToken.hasAccountPlan(accountPlan.getUuid())) {
            throw invalidEx("err.purchase.tokenInvalid");
        }
        return cpToken.getToken();
    }

    public static final String INFO_CODE = "code";

    @Override protected String refund(AccountPlan accountPlan,
                                      AccountPayment payment,
                                      AccountPaymentMethod paymentMethod,
                                      Bill bill,
                                      long refundAmount) {
        log.warn("refund: refunds not supported for "+getClass().getSimpleName()+": accountPlan="+accountPlan.getUuid());
        return INFO_CODE;
    }

}
