package bubble.model.account;

import bubble.dao.account.AccountInitializer;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.SentNotification;
import bubble.model.device.Device;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.filters.auth.TokenPrincipal;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.HasValue;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;
import static org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKeySearchDepth.none;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@ECType(root=true) @ECTypeURIs(listFields={"name", "url", "description", "admin", "suspended"}, isDeleteDefined=false)
@ECTypeFields(list={"name", "url", "description", "admin", "suspended"})
@ECTypeChildren(value={
        @ECTypeChild(type=Device.class, backref="account"),
        @ECTypeChild(type=BubbleApp.class, backref="account"),
        @ECTypeChild(type=AppData.class, backref="account"),
        @ECTypeChild(type=AnsibleRole.class, backref="account"),
        @ECTypeChild(type=CloudService.class, backref="account"),
        @ECTypeChild(type=BubbleDomain.class, backref="account"),
        @ECTypeChild(type=BubbleNetwork.class, backref="account"),
        @ECTypeChild(type=BubbleNode.class, backref="account"),
        @ECTypeChild(type=SentNotification.class, backref="account")
})
@ECSearchDepth(fkDepth=none)
@Entity @NoArgsConstructor @Accessors(chain=true) @Slf4j
public class Account extends IdentifiableBase implements TokenPrincipal, SqlViewSearchResult {

    public static final String[] UPDATE_FIELDS = {"url", "description", "autoUpdatePolicy"};
    public static final String[] ADMIN_UPDATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "suspended", "admin");
    public static final String[] CREATE_FIELDS = ArrayUtil.append(ADMIN_UPDATE_FIELDS, "name");

    public static final String ROOT_USERNAME = "root";
    public static final int NAME_MIN_LENGTH = 4;
    public static final int NAME_MAX_LENGTH = 100;

    public static Account sageMask(Account sage) {
        final Account masked = new Account(sage)
                .setAdmin(false)
                .setHashedPassword(HashedPassword.DISABLED);
        masked.setUuid(sage.getUuid());
        return masked;
    }

    public static String accountField(String table) { return table.equalsIgnoreCase("account") ? "uuid" : "account"; }

    @ECSearchable(filter=true)
    @HasValue(message="err.name.required")
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=100)
    @Getter @Setter private String name;
    public boolean hasName () { return !empty(name); }

    public static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z][-\\.A-Za-z0-9_]+$");
    public boolean hasInvalidName() { return hasName() && !VALID_NAME_PATTERN.matcher(getName()).matches(); }

    private static final List<String> RESERVED_NAMES = Arrays.asList(
            "root", "postmaster", "hostmaster", "webmaster",
            "ftp", "www", "www-data", "postgres", "ipfs",
            "redis", "nginx", "mitmproxy", "mitmdump", "algo", "algovpn");
    public boolean hasReservedName () { return hasName() && RESERVED_NAMES.contains(getName()); }

    // make this updatable if we ever want accounts to be able to change parents
    // there might be a lot more involved in that action though (read-only parent objects that will no longer be visible, must be copied in?)
    @ECIndex @Column(length=UUID_MAXLEN, updatable=false)
    @Getter @Setter private String parent;
    public boolean hasParent () { return parent != null; }

    @ECSearchable(filter=true)
    @Size(max=1024, message="err.url.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(1024+ENC_PAD)+")")
    @Getter @Setter private String url;

    @ECSearchable(filter=true)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable
    @Getter @Setter private Boolean admin = false;
    public boolean admin () { return admin != null && admin; }

    @ECSearchable
    @Getter @Setter private Boolean suspended = false;
    public boolean suspended () { return suspended != null && suspended; }

    @ECSearchable
    @Getter @Setter private Boolean locked = false;
    public boolean locked () { return locked != null && locked; }

    @JsonIgnore @Embedded @Getter @Setter private HashedPassword hashedPassword;

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static ConstraintViolationBean validatePassword(String password) {
        if (password == null) return new ConstraintViolationBean("err.password.required");
        if (password.length() < MIN_PASSWORD_LENGTH) return new ConstraintViolationBean("err.password.tooShort", "Password must be at least "+MIN_PASSWORD_LENGTH+" characters");

        // contains one numeric, one alpha, and one non-alphanumeric
        if (password.matches(".*?\\d+.*?") && password.matches(".*?\\w+.*?") && password.replaceAll("[^A-Za-z0-9]", "").length() != password.length()) {
            return null;
        }
        return new ConstraintViolationBean("err.password.invalid", "Password must contain at least one letter, one number, and one special character");
    }

    @Embedded @Getter @Setter private AutoUpdatePolicy autoUpdatePolicy;

    public boolean wantsNewStuff () { return autoUpdatePolicy != null && autoUpdatePolicy.newStuff(); }
    public boolean wantsDriverUpdates() { return autoUpdatePolicy != null && autoUpdatePolicy.driverUpdates(); }
    public boolean wantsAppUpdates() { return autoUpdatePolicy != null && autoUpdatePolicy.appUpdates(); }
    public boolean wantsDataUpdates() { return autoUpdatePolicy != null && autoUpdatePolicy.dataUpdates(); }

    public static final long INIT_WAIT_INTERVAL = MILLISECONDS.toMillis(250);
    public static final long INIT_WAIT_TIMEOUT = SECONDS.toMillis(60);

    @Transient @JsonIgnore @Getter @Setter private transient AccountInitializer accountInitializer;
    public boolean hasAccountInitializer () { return accountInitializer != null; }

    public Account waitForAccountInit () {
        if (!hasAccountInitializer()) {
            log.warn("waitForAccountInit: accountInitializer was not set");
            return this;
        }
        final long start = now();
        while (!accountInitializer.ready() && now() - start < INIT_WAIT_TIMEOUT) {
            sleep(INIT_WAIT_INTERVAL, "waitForAccountInit: waiting for AccountInitializer.ready");
            if (accountInitializer.hasError()) break;
        }
        if (accountInitializer.hasError()) {
            final Exception error = accountInitializer.getError();
            if (error instanceof RuntimeException) throw (RuntimeException) error;
            return die("waitForAccountInit: "+error);
        }
        if (now() - start > INIT_WAIT_TIMEOUT && !accountInitializer.ready()) {
            throw invalidEx("err.accountInit.timeout");
        }
        log.info("waitForAccountInit: ready in "+formatDuration(now() - start));
        return this;
    }

    @Transient @Getter @Setter private transient String apiToken;

    @Transient public String getToken() { return getApiToken(); }
    public Account setToken(String token) { return setApiToken(token); }

    public Account(Account other) { copy(this, other, CREATE_FIELDS); }

    public Account(ActivationRequest request) {
        setName(request.getName());
        setHashedPassword(new HashedPassword(request.getPassword()));
        setAdmin(true);
        setDescription(request.hasDescription() ? request.getDescription() : "root user");
    }

    public Account(AccountRegistration request) {
        setName(request.getName());
        setHashedPassword(new HashedPassword(request.getPassword()));
    }

    @Override public Identifiable update(Identifiable other) {
        copy(this, other, UPDATE_FIELDS);
        return this;
    }

    @Transient @Getter @Setter private transient AccountPolicy policy;
    public boolean hasPolicy() { return policy != null; }

    @Transient @Getter @Setter private transient String loginRequest;
    @Transient @Getter @Setter private transient AccountContact[] multifactorAuth;

    @Transient @Getter @Setter private transient String remoteHost;
    @Transient @JsonIgnore @Getter @Setter private transient Boolean verifyContact;

    public ValidationResult validateName () {
        final ValidationResult result = new ValidationResult();
        if (!hasName()) {
            result.addViolation("err.name.required");
        } else {
            if (getName().length() < NAME_MIN_LENGTH) {
                result.addViolation("err.name.tooShort");
            } else if (getName().length() > NAME_MAX_LENGTH) {
                result.addViolation("err.name.tooLong");
            }
            if (!admin() && hasReservedName()) {
                result.addViolation("err.name.reserved");
            }
            if (hasInvalidName()) {
                result.addViolation("err.name.regexFailed");
            }
        }
        return result;
    }
}
