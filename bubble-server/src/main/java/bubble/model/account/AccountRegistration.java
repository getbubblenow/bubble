package bubble.model.account;

import lombok.Getter;
import lombok.Setter;

public class AccountRegistration extends Account {

    @Getter @Setter private String password;

    @Getter @Setter private String promoCode;

    @Getter @Setter private AccountContact contact;
    public boolean hasContact () { return contact != null; }

}
