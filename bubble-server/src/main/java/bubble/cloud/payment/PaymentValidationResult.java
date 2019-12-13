package bubble.cloud.payment;

import bubble.model.bill.AccountPaymentMethod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.validation.ConstraintViolationBean.EMPTY_VIOLATION_ARRAY;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentValidationResult {

    @Getter @Setter private AccountPaymentMethod paymentMethod;

    @Getter @Setter private ConstraintViolationBean[] violations;
    public List<ConstraintViolationBean> violationsList() { return Arrays.asList(violations); }

    public PaymentValidationResult(AccountPaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentValidationResult(String error) {
        violations = new ConstraintViolationBean[] { new ConstraintViolationBean(error) };
    }

    public PaymentValidationResult(List<ConstraintViolationBean> violationBeans) {
        violations = violationBeans.toArray(EMPTY_VIOLATION_ARRAY);
    }

    public boolean hasErrors() { return !empty(violations); }

}
