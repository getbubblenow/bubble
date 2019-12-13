package bubble.model.account.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static bubble.ApiConstants.enumFromString;
import static bubble.model.account.message.AccountMessageDirection.from_account;
import static bubble.model.account.message.AccountMessageDirection.to_account;

@AllArgsConstructor
public enum AccountMessageType {

    request      (to_account),
    approval     (from_account),
    denial       (from_account),
    confirmation (to_account),
    notice       (to_account);

    @Getter private final AccountMessageDirection direction;

    @JsonCreator public static AccountMessageType fromString (String v) { return enumFromString(AccountMessageType.class, v); }

    public boolean hasRequest() { return this == approval || this == denial || this == confirmation; }

}
