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

    @Getter @Setter private String account;
    public boolean hasAccount() { return account != null; }

    @Getter @Setter private String accountPlan;
    public boolean hasAccountPlan() { return accountPlan != null; }

    @Getter @Setter private Long expiration;
    public boolean expired() { return expiration != null && now() > expiration; }

    public boolean hasAccountPlan(String accountPlan) {
        return this.accountPlan != null && this.accountPlan.equals(accountPlan);
    }

    @JsonIgnore @Getter @Setter private CloudServiceData cloudServiceData;

}
