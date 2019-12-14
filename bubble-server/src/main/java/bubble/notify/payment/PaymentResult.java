package bubble.notify.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.MultiViolationException;
import org.cobbzilla.wizard.validation.SimpleViolationException;

import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentResult {

    public static final PaymentResult SUCCESS = new PaymentResult().setSuccess(true);
    public static final PaymentResult FAILURE = new PaymentResult().setSuccess(false);

    @Getter @Setter private Boolean success;
    public boolean success() { return success != null && success; }

    @Getter @Setter private ConstraintViolationBean[] violations;
    public boolean hasViolations() { return !empty(violations); }
    public List<ConstraintViolationBean> violationList() { return Arrays.asList(getViolations()); }

    @Getter @Setter private String error;
    public boolean hasError() { return !empty(error); }

    public static PaymentResult exception(Exception e) {
        if (e instanceof SimpleViolationException) {
            return new PaymentResult()
                    .setSuccess(false)
                    .setViolations(new ConstraintViolationBean[]{((SimpleViolationException) e).getBean()});

        } else if (e instanceof MultiViolationException) {
            return new PaymentResult()
                    .setSuccess(false)
                    .setViolations(((MultiViolationException) e).getViolations().toArray(ConstraintViolationBean.EMPTY_VIOLATION_ARRAY));
        } else {
            return new PaymentResult()
                    .setSuccess(false)
                    .setError(errorString(e));
        }
    }

}
