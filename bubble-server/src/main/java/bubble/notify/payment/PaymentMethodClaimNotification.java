package bubble.notify.payment;

import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class PaymentMethodClaimNotification extends SynchronousNotification {

    @Getter @Setter private AccountPaymentMethod paymentMethod;
    public boolean hasPaymentMethod () { return paymentMethod != null; }

    @Getter @Setter private AccountPlan accountPlan;
    public boolean hasAccountPlan() { return accountPlan != null; }

    @Getter @Setter private String cloud;

    public PaymentMethodClaimNotification(String cloud, AccountPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.cloud = cloud;
    }

    public PaymentMethodClaimNotification(String cloud, AccountPlan accountPlan) {
        this.accountPlan = accountPlan;
        this.cloud = cloud;
    }

}
