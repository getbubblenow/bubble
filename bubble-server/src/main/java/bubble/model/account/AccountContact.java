package bubble.model.account;

import bubble.cloud.CloudServiceType;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.HasValue;
import org.cobbzilla.wizard.validation.ValidationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.ApiConstants.G_AUTH;
import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"info", "type"}) @ToString @Slf4j
public class AccountContact implements Serializable {

    public static final int MAX_NICK_LENGTH = 100;
    public static final String[] UPDATE_EXCLUDE_FIELDS = {"uuid", "type", "info"};

    public AccountContact(AccountContact other) { copy(this, other); }

    public AccountContact update(AccountContact other) {
        copy(this, other, null, UPDATE_EXCLUDE_FIELDS);
        return this;
    }

    @Getter @Setter private String uuid = randomUUID().toString();
    public boolean hasUuid () { return !empty(uuid); }
    public AccountContact initUuid () { uuid = randomUUID().toString(); return this; }

    @Getter @Setter private String nick;
    public boolean hasNick () { return !empty(nick); }
    public boolean sameNick (String n) { return !empty(nick) && !empty(n) && nick.equals(n); }

    @HasValue(message="err.info.required")
    @Getter @Setter private String info;

    @JsonIgnore public String getTotpKey () { return totpInfo().getKey(); }
    public TotpBean totpInfo () { return !empty(info) && isAuthenticator() ? json(info, TotpBean.class) : null; }

    @HasValue(message="err.cloudServiceType.required")
    @Getter @Setter private CloudServiceType type;
    @JsonIgnore public boolean isAuthenticator () { return type == CloudServiceType.authenticator; }
    @JsonIgnore public boolean getIsEmail () { return type == CloudServiceType.email; }
    @JsonIgnore public boolean getIsSms () { return type == CloudServiceType.sms; }

    @Getter @Setter private Boolean verified = null;
    public boolean verified () { return bool(verified); }

    @Getter @Setter private Boolean requiredForNetworkOperations = true;
    @Getter @Setter private Boolean requiredForAccountOperations = true;

    @Getter @Setter private Boolean receiveVerifyNotifications = true;
    @Getter @Setter private Boolean receiveLoginNotifications = true;
    @Getter @Setter private Boolean receivePasswordNotification = true;
    @Getter @Setter private Boolean receiveWelcomeMessage = true;
    @Getter @Setter private Boolean receiveInformationalMessages = true;
    @Getter @Setter private Boolean receivePromotionalMessages = true;

    @Getter @Setter private AuthFactorType authFactor = AuthFactorType.not_required;
    public boolean authFactor () { return authFactor != null && authFactor != AuthFactorType.not_required; }
    public boolean requiredAuthFactor () { return authFactor == AuthFactorType.required; }
    public boolean sufficientAuthFactor () { return authFactor == AuthFactorType.sufficient; }
    public boolean requiredForAccountOperations () { return bool(requiredForAccountOperations); }
    public boolean requiredForNetworkOperations() { return bool(requiredForNetworkOperations); }

    public static AccountContact[] set(AccountContact c, AccountContact[] contacts, Account account, BubbleConfiguration configuration) {
        if (!c.getType().isAuthenticationType()) return die("add: not an authentication type: "+c);
        c.setVerified(null); // 'verified' cannot be updated this way; use AccountPolicy.verifyContact

        // validate contact info using type-specific validator
        final List<ConstraintViolationBean> errors = c.getType().validate(c.getInfo());
        if (errors != null && !errors.isEmpty()) throw invalidEx(errors);

        if (c.isAuthenticator()) {
            final AccountContact auth = findAuthenticator(contacts);
            if (auth != null && (!c.hasUuid() || !auth.getUuid().equals(c.getUuid()))) {
                throw invalidEx("err.authenticator.configured", "Only one authenticator can be configured");
            }
        }

        if (!c.hasUuid()) c.initUuid();

        if (contacts == null) contacts = new AccountContact[0];
        final AccountContact existing = Arrays.stream(contacts).filter(contactMatch(c)).findFirst().orElse(null);
        if (existing != null) {
            // updating a contact -- cannot set authFactor if verification is still required
            if (existing.getType().isVerifiableAuthenticationType() && c.authFactor() && !existing.verified()) {
                throw invalidEx("err.contact.unverified", "cannot set authFactor on an unverified contact; verify first");
            }

            // before updating, if we are changing the nick, ensure no other contact already has the nick
            if (c.hasNick() && ( (existing.hasNick() && !existing.getNick().equals(c.getNick())) || !existing.hasNick() )) {
                checkNickInUse(c, contacts);
            }
            if (c.hasNick() && c.getNick().length() > MAX_NICK_LENGTH) {
                throw invalidEx("err.nick.tooLong");
            }
            if (c.isAuthenticator()) {
                // can only change nick on authenticator
                if (c.hasNick()) existing.setNick(c.getNick());
            } else {
                copy(existing, c);
            }

        } else {
            // creating a new contact -- cannot set authFactor for contacts requiring verification
            if (c.getType().isVerifiableAuthenticationType() && c.authFactor()) {
                throw invalidEx("err.contact.unverified", "cannot set authFactor on an unverified contact; verify first");
            }
            // before updating, if we are setting the nick, ensure no other contact already has the nick
            if (c.hasNick()) checkNickInUse(c, contacts);

            // generate secret key if needed
            if (c.isAuthenticator()) {
                if (account == null || configuration == null) {
                    throw invalidEx("err.authenticator.cannotCreate");
                }
                c.setInfo(getTotpInfo(account, configuration));
            }

            return ArrayUtil.append(contacts, c);
        }
        return contacts;
    }

    public static String getTotpInfo(Account account, BubbleConfiguration configuration) {
        final GoogleAuthenticatorKey creds = G_AUTH.createCredentials();
        return json(new TotpBean(creds, account, configuration), COMPACT_MAPPER);
    }

    private static void checkNickInUse(AccountContact c, AccountContact[] contacts) {
        if (Arrays.stream(contacts).anyMatch(cc -> cc.sameNick(c.getNick()))) {
            // there is another contact with the new nick, cannot set it
            throw invalidEx("err.nick.alreadyInUse", "The nick '" + c.getNick() + "' is already in use by another contact", c.getNick());
        }
    }

    private static AccountContact findAuthenticator(AccountContact[] contacts) {
        return contacts == null ? null : Arrays.stream(contacts)
                .filter(c -> c.getType() == CloudServiceType.authenticator)
                .findFirst()
                .orElse(null);
    }

    public static AccountContact[] remove(AccountContact contact, AccountContact[] contacts) {
        if (contacts == null || contacts.length == 0) return contacts;
        final List<AccountContact> contactList = new ArrayList<>(Arrays.asList(contacts));
        return contactList.removeIf(contactMatch(contact))
                ? contactList.toArray(new AccountContact[contacts.length-1])
                : contacts;
    }

    protected static Predicate<AccountContact> contactMatch(AccountContact contact) {
        if (contact == null) return c -> false;
        return c -> c.getUuid().equals(contact.getUuid())
                || (c.isAuthenticator() && contact.isAuthenticator())
                || (c.getType() == contact.getType() && c.getInfo().equals(contact.getInfo()));
    }

    public boolean isAllowed(AccountMessage message) {
        final AccountMessageType type = message.getMessageType();
        final AccountAction action = message.getAction();
        final ActionTarget target = message.getTarget();

        if (!verified()) {
            if ( target == ActionTarget.account && (
                    (action == AccountAction.verify)  // all verification-related messages are allowed to unverified
                 || (type == AccountMessageType.notice && action == AccountAction.welcome) // welcome is allowed to unverified
            ) ) {
                log.info("isAllowed(" + message.getAction() + "): allowing "+type+" message to unverified contact: "+action);
            } else {
                log.info("isAllowed(" + message.getAction() + "): "+type+" messages to unverified contacts are not allowed, except for verify/welcome");
                return false;
            }
        }

        switch (action) {
            case payment:
                switch (type) {
                    case request: case notice:
                        return target == ActionTarget.network && getType() != CloudServiceType.authenticator;
                    default:
                        log.warn("isAllowed(payment): unknown type: "+type+" for message, returning false");
                        return false;
                }

            case login:
                switch (type) {
                    case request:
                        return (target == ActionTarget.account && (getType() == CloudServiceType.authenticator || authFactor()));
                    case approval: case denial:
                        return target == ActionTarget.account && bool(receiveLoginNotifications);
                    default:
                        log.warn("isAllowed(login): unknown type: "+type+" for message, returning false");
                        return false;
                }

            case password:
                switch (type) {
                    case request: case approval: case denial:
                        return (
                                target == ActionTarget.account
                                        && bool(receivePasswordNotification)
                                        && getType() != CloudServiceType.authenticator
                                        && verified()
                        ) || (
                                target == ActionTarget.network
                                        && bool(requiredForNetworkOperations)
                                        && getType() != CloudServiceType.authenticator
                                        && verified()
                        );
                    case confirmation:
                        return target == ActionTarget.network
                                && bool(requiredForNetworkOperations)
                                && getType() != CloudServiceType.authenticator
                                && verified();
                    default:
                        log.warn("isAllowed(password): unknown type: "+type+" for message, returning false");
                        return false;
                }

            case download:
                switch (type) {
                    case request: case approval: case denial: case confirmation:
                        return target == ActionTarget.account;
                    default:
                        log.warn("isAllowed(download): unknown type: "+type+" for message, returning false");
                        return false;
                }

            case verify:
                if (type == AccountMessageType.request) {
                    if (target == ActionTarget.account && getType().isVerifiableAuthenticationType()) {
                        if (message.hasContact() && message.getContact().equals(getUuid())) return true;
                        return bool(receiveVerifyNotifications);
                    } else if (target == ActionTarget.network && requiredForNetworkOperations()) {
                        return true;
                    } else {
                        log.warn("isAllowed(verify): verify action not allowed for type/target: "+getType()+"/"+target);
                    }
                    return false;
                }
                log.warn("isAllowed(verify): unknown type: " + type + " for message, returning false");
                return false;

            case start: case stop: case delete:
                switch (target) {
                    case account:            return bool(requiredForAccountOperations);
                    case node: case network: return bool(requiredForNetworkOperations);
                    default:
                        log.warn("isAllowed(start/stop/delete): unknown target: "+target+", returning false");
                        return false;
                }

            case welcome: return bool(receiveWelcomeMessage);
            case info:    return bool(receiveInformationalMessages);
            case promo:   return bool(receivePromotionalMessages);

            default:
                log.warn("isAllowed: unknown operations: "+action+", returning false");
                return false;
        }
    }

    public AccountContact mask() {
        return new AccountContact(this).setInfo(getType().mask(getInfo()));
    }

    public static Collection<AccountContact> mask(Collection<AccountContact> contacts) {
        return empty(contacts) ? contacts : contacts.stream().map(c -> c.mask()).collect(Collectors.toList());
    }

    public ValidationResult validate(ValidationResult errors) {
        if (type == null) {
            errors.addViolation("err.contactType.required");
        } else {
            type.validate(info).forEach(errors::addViolation);
        }
        return errors;
    }
}
