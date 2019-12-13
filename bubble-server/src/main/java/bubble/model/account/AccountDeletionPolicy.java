package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountDeletionPolicy {

    block_delete, full_delete;

    @JsonCreator public static AccountDeletionPolicy fromString (String v) { return enumFromString(AccountDeletionPolicy.class, v); }

}
