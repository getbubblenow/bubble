package bubble.cloud.payment.code;

import bubble.model.cloud.CloudServiceData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor @Accessors(chain=true)
public class CodePaymentToken {

    @Getter @Setter private String token;

    @Getter @Setter private String accountPaymentMethod;
    public boolean hasAccountPaymentMethod() { return accountPaymentMethod != null; }

    @Getter @Setter private String accountPlanPaymentMethod;
    public boolean hasAccountPlanPaymentMethod() { return accountPlanPaymentMethod != null; }

    @Getter @Setter private Long expiration;
    public boolean expired() { return expiration != null && now() > expiration; }

    public boolean hasPaymentMethod(String planPaymentMethod) {
        return accountPlanPaymentMethod != null && accountPlanPaymentMethod.equals(planPaymentMethod);
    }

    @JsonIgnore @Getter @Setter private CloudServiceData cloudServiceData;

}
