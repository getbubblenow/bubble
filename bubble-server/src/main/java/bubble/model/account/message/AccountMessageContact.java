package bubble.model.account.message;

import bubble.model.account.AccountContact;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class AccountMessageContact implements Serializable {

    @Getter @Setter private AccountMessage message;
    @Getter @Setter private AccountContact contact;

    public String key() { return message.getUuid()+":"+contact.getUuid(); }

}
