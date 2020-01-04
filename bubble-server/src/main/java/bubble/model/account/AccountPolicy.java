package bubble.model.account;

import bubble.cloud.CloudServiceType;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.ActionTarget;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static bubble.model.account.AccountContact.contactMatch;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@Entity @ECType(root=true) @ECTypeCreate(method="DISABLED")
@NoArgsConstructor @Accessors(chain=true)
public class AccountPolicy extends IdentifiableBase implements HasAccount {

    public static final Long MAX_ACCOUNT_OPERATION_TIMEOUT = TimeUnit.DAYS.toMillis(3);
    public static final Long MAX_NODE_OPERATION_TIMEOUT = TimeUnit.DAYS.toMillis(3);
    public static final Long MIN_ACCOUNT_OPERATION_TIMEOUT = MINUTES.toMillis(1);
    public static final Long MIN_NODE_OPERATION_TIMEOUT = MINUTES.toMillis(1);

    public static final String[] UPDATE_FIELDS = {"deletionPolicy", "nodeOperationTimeout", "accountOperationTimeout"};

    public AccountPolicy(AccountPolicy policy) { copy(this, policy); }

    @Override public Identifiable update(Identifiable thing) { copy(this, thing, UPDATE_FIELDS); return this; }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String account;

    @JsonIgnore @Override public String getName() { return getAccount(); }

    @ECSearchable(type=EntityFieldType.expiration_time) @ECField(index=20)
    @Type(type=ENCRYPTED_LONG) @Column(columnDefinition="varchar("+ENC_LONG+") NOT NULL")
    @Getter @Setter private Long nodeOperationTimeout = MINUTES.toMillis(30);

    @ECSearchable(type=EntityFieldType.expiration_time) @ECField(index=30)
    @Type(type=ENCRYPTED_LONG) @Column(columnDefinition="varchar("+ENC_LONG+") NOT NULL")
    @Getter @Setter private Long accountOperationTimeout = MINUTES.toMillis(10);

    @ECSearchable @ECField(index=40)
    @Enumerated(EnumType.STRING) @Column(length=40, nullable=false)
    @Getter @Setter private AccountDeletionPolicy deletionPolicy = AccountDeletionPolicy.block_delete;

    @JsonIgnore @Transient public boolean isFullDelete () { return deletionPolicy == AccountDeletionPolicy.full_delete; }
    @JsonIgnore @Transient public boolean isBlockDelete () { return deletionPolicy == AccountDeletionPolicy.block_delete; }

    @ECSearchable(filter=true) @ECField(index=50)
    @Size(max=100000, message="err.accountContactsJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String accountContactsJson;
    public boolean hasAccountContacts() { return accountContactsJson != null; }
    public boolean hasVerifiedAccountContacts() {
        return hasAccountContacts() && Arrays.stream(getAccountContacts()).anyMatch(AccountContact::verified);
    }

    @Transient public AccountContact[] getAccountContacts () { return accountContactsJson == null ? null : json(accountContactsJson, AccountContact[].class); }
    public AccountPolicy setAccountContacts(AccountContact[] contacts) { return setAccountContactsJson(contacts == null ? null : json(contacts)); }

    public AccountPolicy setContact(AccountContact c) { return setContact(c, null, null); }

    public AccountPolicy setContact(AccountContact c, Account account, BubbleConfiguration configuration) {
        setAccountContacts(AccountContact.set(c, getAccountContacts(), account, configuration));
        return this;
    }
    public AccountPolicy removeContact(AccountContact c) { setAccountContacts(AccountContact.remove(c, getAccountContacts())); return this; }

    public List<AccountContact> getAllowedContacts(AccountMessage message) {
        if (!hasAccountContacts()) return Collections.emptyList();
        return Arrays.stream(getAccountContacts()).filter(c -> c.isAllowed(message)).collect(Collectors.toList());
    }

    public List<AccountContact> getRequiredApprovals(AccountMessage message) {
        if (!hasAccountContacts()) return Collections.emptyList();
        switch (message.getTarget()) {
            case account:
                if (message.getAction() == AccountAction.password || message.getAction() == AccountAction.login) {
                    return requiredAuthFactors();
                } else {
                    return Arrays.stream(getAccountContacts())
                            .filter(c -> c.requiredForAccountOperations() || c.requiredAuthFactor())
                            .collect(Collectors.toList());
                }
            case network: case node:
                return Arrays.stream(getAccountContacts())
                        .filter(c -> c.requiredForNodeOperations() || c.requiredAuthFactor())
                        .collect(Collectors.toList());
            default:
                return requiredAuthFactors();
        }
    }

    public List<AccountContact> requiredAuthFactors() {
        return Arrays.stream(getAccountContacts())
                .filter(AccountContact::requiredAuthFactor)
                .collect(Collectors.toList());
    }

    public List<AccountContact> getSufficientApprovals(AccountMessage message) {
        if (!hasAccountContacts()) return Collections.emptyList();
        return Arrays.stream(getAccountContacts())
                .filter(AccountContact::sufficientAuthFactor)
                .collect(Collectors.toList());
    }

    @Transient @JsonIgnore public List<AccountContact> getAuthFactors() {
        if (!hasAccountContacts()) return Collections.emptyList();
        return Arrays.stream(getAccountContacts()).filter(AccountContact::authFactor).collect(Collectors.toList());
    }
    public AuthFactorType getAuthFactor(String uuid) {
        if (empty(uuid) || !hasAccountContacts()) return null;
        final AccountContact contact = Arrays.stream(getAccountContacts()).filter(c -> c.getUuid().equals(uuid)).findFirst().orElse(null);
        return contact == null ? null : contact.getAuthFactor();
    }

    public String contactsString () {
        final StringBuilder b = new StringBuilder();
        if (hasAccountContacts()) {
            Arrays.stream(getAccountContacts()).forEach(c -> {
                if (b.length() > 0) b.append(", ");
                b.append(c.getType().name()).append(":").append(c.getInfo());
            });
        }
        return b.toString();
    }

    @JsonIgnore @Transient public String getFirstEmail () { return findFirst(CloudServiceType.email); }
    @JsonIgnore @Transient public String getFirstVerifiedEmail () { return findFirstVerified(CloudServiceType.email); }
    @JsonIgnore @Transient public String getFirstSms () { return findFirst(CloudServiceType.sms); }
    @JsonIgnore @Transient public String getFirstAuth () { return findFirst(CloudServiceType.authenticator); }

    public String findFirst(CloudServiceType type) {
        if (!hasAccountContacts()) return null;
        final AccountContact contact = Arrays.stream(getAccountContacts()).filter(c -> c.getType() == type).findFirst().orElse(null);
        return contact == null ? null : contact.getInfo();
    }

    public String findFirstVerified(CloudServiceType type) {
        if (!hasAccountContacts()) return null;
        final AccountContact contact = Arrays.stream(getAccountContacts())
                .filter(c -> c.getType() == type && c.verified())
                .findFirst().orElse(null);
        return contact == null ? null : contact.getInfo();
    }

    public boolean hasContact(String info) {
        if (!hasAccountContacts()) return false;
        return Arrays.stream(getAccountContacts()).anyMatch(c -> c.getInfo().equals(info));
    }
    public AccountContact findContact(AccountContact contact) {
        return findContact(contact, getAccountContacts());
    }
    public AccountContact findContactByUuid(String uuid) {
        return findContact(new AccountContact().setUuid(uuid), getAccountContacts());
    }

    protected static AccountContact findContact(AccountContact contact, AccountContact[] accountContacts) {
        if (accountContacts == null || accountContacts.length == 0) return null;
        return Arrays.stream(accountContacts).filter(contactMatch(contact)).findFirst().orElse(null);
    }

    @JsonIgnore @Transient public AccountContact getAuthenticator () {
        if (!hasAccountContacts()) return null;
        return Arrays.stream(getAccountContacts()).filter(AccountContact::isAuthenticator).findFirst().orElse(null);
    }

    public Long getTimeout(AccountMessage message) { return getTimeout(message.getTarget()); }

    public Long getTimeout(ActionTarget target) {
        switch (target) {
            case account:            return accountOperationTimeout;
            case node: case network: return nodeOperationTimeout;
            default: return die("getTimeout: invalid target: "+ target);
        }
    }

    public AccountPolicy verifyContact(String contactUuid) {
        if (hasAccountContacts()) {
            final AccountContact[] contacts = getAccountContacts();
            for (AccountContact c : contacts) {
                if (c.getUuid().equals(contactUuid)) {
                    c.setVerified(true);
                    if (c.isAuthenticator()) c.setAuthFactor(AuthFactorType.required);
                    break;
                }
            }
            setAccountContacts(contacts);
        }
        return this;
    }

    public ValidationResult validate() {
        final ValidationResult result = new ValidationResult();
        if (deletionPolicy == null) {
            result.addViolation("err.deletionPolicy.required");
        }
        if (accountOperationTimeout == null) {
            result.addViolation("err.accountOperationTimeout.required");
        } else if (accountOperationTimeout > MAX_ACCOUNT_OPERATION_TIMEOUT) {
            result.addViolation("err.accountOperationTimeout.tooLong");
        } else if (accountOperationTimeout < MIN_ACCOUNT_OPERATION_TIMEOUT) {
            result.addViolation("err.accountOperationTimeout.tooShort");
        }
        if (nodeOperationTimeout == null) {
            result.addViolation("err.nodeOperationTimeout.required");
        } else if (nodeOperationTimeout > MAX_NODE_OPERATION_TIMEOUT) {
            result.addViolation("err.nodeOperationTimeout.tooLong");
        } else if (nodeOperationTimeout < MIN_NODE_OPERATION_TIMEOUT) {
            result.addViolation("err.nodeOperationTimeout.tooShort");
        }
        return result;
    }

    public AccountPolicy mask() {
        if (hasAccountContacts()) {
            final List<AccountContact> scrubbed = new ArrayList<>();
            for (AccountContact c : getAccountContacts()) {
                scrubbed.add(c.mask());
            }
            setAccountContacts(scrubbed.toArray(new AccountContact[0]));
        }
        return this;
    }
}
