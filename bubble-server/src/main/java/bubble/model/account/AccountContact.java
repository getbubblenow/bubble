package bubble.model.account;

import bubble.cloud.CloudServiceType;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.ActionTarget;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.List;
import java.util.function.Predicate;

import static bubble.ApiConstants.G_AUTH;
import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"info", "type"}) @ToString @Slf4j
public class AccountContact implements Serializable {

    @Getter @Setter private String uuid = randomUUID().toString();
    @Getter @Setter private String nick;
    public boolean hasNick () { return !empty(nick); }
    public boolean sameNick (String n) { return !empty(nick) && !empty(n) && nick.equals(n); }

    @HasValue(message="err.info.required")
    @Getter @Setter private String info;

    @HasValue(message="err.cloudServiceType.required")
    @Getter @Setter private CloudServiceType type;
    @JsonIgnore public boolean isAuthenticator () { return type == CloudServiceType.authenticator; }

    @Getter @Setter private Boolean verified = null;
    public boolean verified () { return bool(verified); }

    @Getter @Setter private Boolean requiredForNetworkUnlock = true;
    @Getter @Setter private Boolean requiredForNodeOperations = true;
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
    public boolean requiredForAccountOperations () { return requiredForAccountOperations != null && requiredForAccountOperations; }
    public boolean requiredForNetworkUnlock () { return requiredForNetworkUnlock != null && requiredForNetworkUnlock; }
    public boolean requiredForNodeOperations () { return requiredForNodeOperations != null && requiredForNodeOperations; }

    public static AccountContact[] set(AccountContact c, AccountContact[] contacts) {
        if (!c.getType().isAuthenticationType()) return die("add: not an authentication type: "+c);
        c.setVerified(null); // 'verified' cannot be updated this way; use AccountPolicy.verifyContact

        // validate contact info using type-specific validator
        final List<ConstraintViolationBean> errors = c.getType().validate(c.getInfo());
        if (errors != null && !errors.isEmpty()) throw invalidEx(errors);

        // there must be at least one contact that can be used to unlock the network
        if (!c.requiredForNetworkUnlock() && Arrays.stream(contacts).noneMatch(AccountContact::requiredForNetworkUnlock)) {
            throw invalidEx("err.contact.atLeastOneNetworkUnlockContactRequired");
        }

        if (c.isAuthenticator()) {
            final AccountContact auth = findAuthenticator(contacts);
            if (auth != null && !auth.getUuid().equals(c.getUuid())) {
                throw invalidEx("err.authenticator.configured", "Only one authenticator can be configured");
            }
            // authenticator is always a required factor, and is always verified
            c.setAuthFactor(AuthFactorType.required);
            c.setVerified(true);
        }

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
            if (c.isAuthenticator()) c.setInfo(G_AUTH.createCredentials().getKey());

            return ArrayUtil.append(contacts, c);
        }
        return contacts;
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

        if (!verified()
                && type == AccountMessageType.request
                && action != AccountAction.verify
                && target != ActionTarget.account
                && getType().isVerifiableAuthenticationType()) {
            log.warn("isAllowed("+message.getAction()+"): requests to unverified contacts are not allowed, except to verify them");
            return false;
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
                                        && bool(requiredForNodeOperations)
                                        && getType() != CloudServiceType.authenticator
                                        && verified()
                        );
                    case confirmation:
                        return target == ActionTarget.network
                                && bool(requiredForNodeOperations)
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
                    } else if (target == ActionTarget.network && bool(requiredForNetworkUnlock)) {
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
                    case node: case network: return bool(requiredForNodeOperations);
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
        return new AccountContact()
                .setNick(getNick())
                .setType(getType())
                .setInfo(getType().mask(getInfo()));
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
