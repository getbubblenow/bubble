package bubble.model.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import bubble.model.cloud.CloudService;
import bubble.notify.payment.PaymentValidationResult;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.filters.Scrubbable;
import org.cobbzilla.wizard.filters.ScrubbableField;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Entity @ECType(root=true) @ECTypeCreate(method="DISABLED")
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"paymentMethodType", "paymentInfo"}) })
@Slf4j
public class AccountPaymentMethod extends IdentifiableBase implements HasAccountNoName, Scrubbable {

    // do not send paymentInfo over the wire
    public static final ScrubbableField[] SCRUB_FIELDS = {
            new ScrubbableField(AccountPaymentMethod.class, "paymentInfo", String.class)
    };

    @Override public ScrubbableField[] fieldsToScrub() { return SCRUB_FIELDS; }

    public static final String[] CREATE_FIELDS = {"paymentMethodType", "paymentInfo", "maskedPaymentInfo", "cloud"};
    public static final String[] VALIDATION_SET_FIELDS = {"paymentInfo", "maskedPaymentInfo"};

    public AccountPaymentMethod(AccountPaymentMethod other) { copy(this, other, CREATE_FIELDS); }

    @Override public void beforeCreate() { if (!hasUuid()) initUuid(); }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=CloudService.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String cloud;
    public boolean hasCloud() { return cloud != null; }

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=Promotion.class)
    @Column(length=UUID_MAXLEN, updatable=false)
    @Getter @Setter private String promotion;
    public boolean hasPromotion() { return promotion != null; }

    @ECSearchable @ECField(index=40)
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private PaymentMethodType paymentMethodType;
    public boolean hasPaymentMethodType() { return paymentMethodType != null; }

    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String paymentInfo;
    public boolean hasPaymentInfo () { return paymentInfo != null; }

    public static final String DEFAULT_MASKED_PAYMENT_INFO = "XXXX-".repeat(3)+"XXXX";
    @ECSearchable @ECField(index=50, type=EntityFieldType.opaque_string)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String maskedPaymentInfo = DEFAULT_MASKED_PAYMENT_INFO;

    @ECSearchable @ECField(index=60)
    @Getter @Setter private Long deleted = null;
    public AccountPaymentMethod setDeleted () { return setDeleted(now()); }
    public boolean deleted() { return deleted != null; }
    public boolean notDeleted() { return !deleted(); }

    public ValidationResult validate(ValidationResult result, BubbleConfiguration configuration) {

        if (!hasPaymentMethodType()) {
            result.addViolation("err.paymentMethodType.required");
            return result;
        }
        final CloudServiceDAO cloudDAO = configuration.getBean(CloudServiceDAO.class);

        final CloudService paymentCloud;
        if (!hasCloud()) {
            // try to find a cloud payment service that supports this payment method type
            paymentCloud = cloudDAO.findByAccountAndType(account, CloudServiceType.payment).stream()
                    .filter(c -> c.getPaymentDriver(configuration).getPaymentMethodType() == getPaymentMethodType())
                    .findFirst()
                    .orElse(null);
            if (paymentCloud == null) {
                result.addViolation("err.paymentService.required");
            } else {
                setCloud(paymentCloud.getUuid());
                log.warn("No payment service specified, using: "+paymentCloud.getUuid()+"/"+paymentCloud.getName());
            }
        } else {
            paymentCloud = cloudDAO.findByAccountAndId(account, cloud);
            if (paymentCloud == null) {
                result.addViolation("err.paymentService.required");
            } else {
                if (paymentCloud.getType() != CloudServiceType.payment) {
                    result.addViolation("err.paymentService.notPayment");
                } else {
                    setCloud(paymentCloud.getUuid());
                }
            }
        }
        if (paymentCloud != null) {
            final PaymentServiceDriver paymentDriver = paymentCloud.getPaymentDriver(configuration);
            if (paymentDriver.getPaymentMethodType() != getPaymentMethodType()) {
                result.addViolation("err.paymentMethodType.mismatch");
            } else {
                if (empty(getPaymentInfo())) {
                    result.addViolation("err.paymentInfo.required");
                } else {
                    final PaymentValidationResult validationResult = paymentDriver.validate(this);
                    if (validationResult.hasErrors()) {
                        result.addAll(validationResult.getViolations());
                    } else {
                        copy(this, validationResult.getPaymentMethod(), VALIDATION_SET_FIELDS);
                    }
                }
            }
        }

        return result;
    }

    @JsonIgnore @Transient public String getCacheKey () {
        return hashOf(getUuid(), account, cloud, paymentMethodType, paymentInfo);
    }
}
