package bubble.notify.payment;

import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentMethodClaimNotification extends SynchronousNotification {

    @Getter @Setter private AccountPaymentMethod paymentMethod;
    public boolean hasPaymentMethod () { return paymentMethod != null; }

    @Getter @Setter private AccountPlanPaymentMethod planPaymentMethod;
    public boolean hasPlanPaymentMethod () { return planPaymentMethod != null; }

    @Getter @Setter private String cloud;

    public PaymentMethodClaimNotification(AccountPaymentMethod paymentMethod, String cloud) {
        this.paymentMethod = paymentMethod;
        this.cloud = cloud;
    }

    public PaymentMethodClaimNotification(AccountPlanPaymentMethod planPaymentMethod, String cloud) {
        this.planPaymentMethod = planPaymentMethod;
        this.cloud = cloud;
    }

}
