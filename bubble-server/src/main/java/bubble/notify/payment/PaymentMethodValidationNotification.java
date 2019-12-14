package bubble.notify.payment;

import bubble.model.bill.AccountPaymentMethod;
import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class PaymentMethodValidationNotification extends SynchronousNotification {

    @Getter @Setter private String cloud;
    @Getter @Setter private AccountPaymentMethod paymentMethod;

}
