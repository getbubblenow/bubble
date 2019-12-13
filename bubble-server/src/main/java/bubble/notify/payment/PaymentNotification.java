package bubble.notify.payment;

import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class PaymentNotification extends SynchronousNotification {

    @Getter @Setter private String accountPlanUuid;
    @Getter @Setter private String paymentMethodUuid;
    @Getter @Setter private String billUuid;
    @Getter @Setter private int purchaseAmount;
    @Getter @Setter private String currency;

}
