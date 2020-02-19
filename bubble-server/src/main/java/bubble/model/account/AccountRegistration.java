package bubble.model.account;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AccountRegistration extends Account {

    @Getter @Setter private String password;

    @Getter @Setter private String promoCode;
    public boolean hasPromoCode () { return !empty(promoCode); }

    @Getter @Setter private AccountContact contact;
    public boolean hasContact () { return contact != null; }

}
