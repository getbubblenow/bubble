package bubble.cloud.payment;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.CloudServiceType;

public abstract class PaymentDriverBase<T> extends CloudServiceDriverBase<T> implements PaymentServiceDriver {

    @Override public CloudServiceType getType() { return CloudServiceType.payment; }

}
