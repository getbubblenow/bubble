/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.AccountPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true)
public class SyncAccountNotification {
    @Getter @Setter private String accountUuid;
    @Getter @Setter private String updatedHashedPassword;
    @Getter @Setter private AccountPolicyIncludingJSONContacts updatedPolicy;

    public SyncAccountNotification(String accountUuid, String updatedHashedPassword, AccountPolicy updatedPolicy) {
        this.accountUuid = accountUuid;
        this.updatedHashedPassword = updatedHashedPassword;
        if (updatedPolicy != null) {
            this.updatedPolicy = new AccountPolicyIncludingJSONContacts(updatedPolicy);
        }
    }

    @NoArgsConstructor
    public class AccountPolicyIncludingJSONContacts extends AccountPolicy {
        public AccountPolicyIncludingJSONContacts(@NonNull final AccountPolicy policy) { copy(this, policy); }

        @Override @JsonIgnore(false) @JsonProperty
        public String getAccountContactsJson() { return super.getAccountContactsJson(); }
    }
}
