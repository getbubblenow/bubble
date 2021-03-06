/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import bubble.dao.account.AccountInitializer;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.bill.*;
import bubble.model.boot.ActivationRequest;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.ReceivedNotification;
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
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
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
import java.util.List;

import static bubble.ApiConstants.ACCOUNTS_ENDPOINT;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.string.ValidationRegexes.EMAIL_PATTERN;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldMode.readOnly;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.epoch_time;
import static org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKeySearchDepth.none;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@ECType(root=true)
@ECTypeURIs(baseURI=ACCOUNTS_ENDPOINT, listFields={"email", "url", "description", "admin", "suspended"}, isDeleteDefined=false)
@ECTypeChildren(uriPrefix=ACCOUNTS_ENDPOINT+"/{Account.email}", value={
        @ECTypeChild(type=Device.class, backref="account"),
        @ECTypeChild(type=RuleDriver.class, backref="account"),
        @ECTypeChild(type=BubbleApp.class, backref="account"),
        @ECTypeChild(type=AppData.class, backref="account"),
        @ECTypeChild(type=CloudService.class, backref="account"),
        @ECTypeChild(type=BubbleFootprint.class, backref="account"),
        @ECTypeChild(type=BubbleDomain.class, backref="account"),
        @ECTypeChild(type=BubbleNetwork.class, backref="account"),
        @ECTypeChild(type=BubbleNode.class, backref="account"),
        @ECTypeChild(type=AccountPlan.class, backref="account"),
        @ECTypeChild(type=AccountSshKey.class, backref="account"),
        @ECTypeChild(type=Bill.class, backref="account"),
        @ECTypeChild(type=AccountPaymentMethod.class, backref="account"),
        @ECTypeChild(type=AccountPayment.class, backref="account"),
        @ECTypeChild(type=SentNotification.class, backref="account"),
        @ECTypeChild(type=ReceivedNotification.class, backref="account")
})
@ECSearchDepth(fkDepth=none)
@Entity @NoArgsConstructor @Accessors(chain=true) @Slf4j
public class Account extends IdentifiableBaseParentEntity implements TokenPrincipal, SqlViewSearchResult {

    public static final String[] UPDATE_FIELDS = {
            "url", "description", "autoUpdatePolicy", "sync", "preferredPlan", "showBlockStats"
    };
    public static final String[] ADMIN_UPDATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "suspended", "admin");
    public static final String[] CREATE_FIELDS = ArrayUtil.append(ADMIN_UPDATE_FIELDS,
            "name", "termsAgreed");

    public static final String ROOT_USERNAME = "root";
    public static final String ROOT_EMAIL = "root@local.local";
    public static final int EMAIL_MAX_LENGTH = 100;

    public static Account sageMask(Account sage) {
        final Account masked = new Account(sage)
                .setAdmin(false)
                .setSage(true)
                .setDeleted(now())
                .setHashedPassword(HashedPassword.DISABLED);
        masked.setUuid(sage.getUuid());
        masked.setCtime(sage.getCtime());
        return masked;
    }

    public static String accountField(String table) { return table.equalsIgnoreCase("account") ? UUID : "account"; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.email.required")
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=EMAIL_MAX_LENGTH)
    @Getter private String email;
    public Account setEmail(String n) { this.email = n == null ? null : n.toLowerCase().trim(); return this; }
    public boolean hasEmail() { return !empty(email); }

    @Override @Transient public String getName() { return getEmail(); }
    public Account setName(String n) { return setEmail(n); }

    // make this updatable if we ever want accounts to be able to change parents
    // there might be a lot more involved in that action though (read-only parent objects that will no longer be visible, must be copied in?)
    @ECForeignKey(entity=Account.class) @ECField(index=20, mode=readOnly)
    @Column(length=UUID_MAXLEN, updatable=false)
    @Getter @Setter private String parent;
    public boolean hasParent () { return parent != null; }

    @ECSearchable @ECField(index=30)
    @Size(max=1024, message="err.url.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(1024+ENC_PAD)+")")
    @Getter @Setter private String url;

    @ECSearchable @ECField(index=40)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable @ECField(index=50, required=EntityFieldRequired.optional)
    @Size(max=20, message="err.locale.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(20+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String locale = getDEFAULT_LOCALE();
    public boolean hasLocale () { return !empty(locale); }

    @ECIndex @ECSearchable @ECField(index=60)
    @Getter @Setter private Boolean admin = false;
    public boolean admin () { return bool(admin); }

    // set in SessionDAO so UI can know if the user is first admin
    @Transient @Getter @Setter private boolean firstAdmin = false;

    @ECIndex(unique=true, where="sage = true") @ECField(index=70)
    @Getter @Setter private Boolean sage = false;
    public boolean sage () { return bool(sage); }

    @ECIndex @ECSearchable @ECField(index=80)
    @Getter @Setter private Boolean suspended = false;
    public boolean suspended () { return bool(suspended); }

    @ECIndex @ECSearchable @ECField(index=90)
    @Getter @Setter private Boolean locked = false;
    public boolean locked () { return bool(locked); }

    @ECIndex @ECSearchable @ECField(index=100, type=epoch_time, mode=readOnly)
    @Getter @Setter private Long deleted;
    public boolean deleted () { return deleted != null; }
    public boolean notDeleted () { return !deleted(); }
    public Account setDeleted() { return setDeleted(now()); }

    @ECIndex @ECSearchable @ECField(index=110, type=epoch_time, mode=readOnly)
    @Getter @Setter private Long lastLogin;
    public Account setLastLogin() { return setLastLogin(now()); }

    @Getter @Setter @Transient private transient Boolean firstLogin;

    @ECIndex @ECSearchable @ECField(index=120, type=epoch_time, mode=readOnly)
    @Column(nullable=false)
    @Getter @Setter private Long termsAgreed;
    public Account setTermsAgreed() { return setTermsAgreed(now()); }

    @ECField(index=130)
    @Getter @Setter private Boolean sync;
    public boolean sync() { return sync == null || sync; }

    @ECField(index=140)
    @Getter @Setter private Boolean showBlockStats;
    public boolean showBlockStats() { return showBlockStats == null || showBlockStats; }

    @JsonIgnore @Transient @Getter @Setter private boolean refreshShowBlockStats;

    @JsonIgnore @Embedded @Getter private HashedPassword hashedPassword;
    public Account setHashedPassword (HashedPassword newPass) {
        this.hashedPassword = newPass;
        this.hashedPasswordChanged = true;
        return this;
    }
    @JsonIgnore @Transient public boolean isSpecialHashedPassword () { return hashedPassword != null && hashedPassword.isSpecial(); }
    @JsonIgnore @Transient public boolean isNotSpecialHashedPassword () { return hashedPassword != null && !hashedPassword.isSpecial(); }

    @JsonIgnore @Transient @Getter @Setter private boolean hashedPasswordChanged = false;
    @JsonIgnore @Transient @Getter @Setter private String previousPasswordHash;
    @JsonIgnore @Transient @Getter @Setter private Boolean skipSync;
    public boolean skipSync() { return bool(skipSync); }

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

    @Column(length=UUID_MAXLEN)
    @ECForeignKey(entity=BubblePlan.class, index=false, cascade=false)
    @Getter @Setter private String preferredPlan;
    public boolean hasPreferredPlan () { return !empty(preferredPlan); }

    @Embedded @Getter @Setter private AutoUpdatePolicy autoUpdatePolicy;

    public boolean wantsJarUpdates() { return autoUpdatePolicy != null && autoUpdatePolicy.jarUpdates(); }
    public boolean wantsAppUpdates() { return autoUpdatePolicy != null && autoUpdatePolicy.appUpdates(); }

    public static final long INIT_WAIT_INTERVAL = MILLISECONDS.toMillis(250);
    public static final long INIT_WAIT_TIMEOUT = SECONDS.toMillis(60);

    @Transient @Getter @Setter private transient String promoError;

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
    public boolean hasToken () { return !empty(getApiToken()); }

    public Account(Account other) { copy(this, other, CREATE_FIELDS); }

    public Account(ActivationRequest request) {
        setEmail(request.getEmail());
        setHashedPassword(new HashedPassword(request.getPassword()));
        setAdmin(true);
        setDescription(request.hasDescription() ? request.getDescription() : "root user");
        setLocale(getDEFAULT_LOCALE());
        setTermsAgreed(now());
    }

    public Account(AccountRegistration request) {
        setEmail(request.getEmail());
        setHashedPassword(new HashedPassword(request.getPassword()));
        setTermsAgreed(request.getTermsAgreed());
        setPreferredPlan(request.getPreferredPlan());
    }

    @Override public Identifiable update(Identifiable other) {
        copy(this, other, UPDATE_FIELDS);
        return this;
    }

    @Transient @Getter @Setter private transient AccountPolicy policy;
    public boolean hasPolicy() { return policy != null; }

    @Transient @Getter @Setter private transient Boolean sendWelcomeEmail = null;
    public boolean sendWelcomeEmail() { return sendWelcomeEmail == null || sendWelcomeEmail; }

    @Transient @Getter @Setter private transient String loginRequest;
    @Transient @Getter private transient AccountContact[] multifactorAuth;
    public Account setMultifactorAuth(AccountContact[] mfa) {
        if (!empty(mfa)) {
            final AccountContact[] masked = new AccountContact[mfa.length];
            for (int i=0; i<mfa.length; i++) masked[i] = mfa[i].mask();
            this.multifactorAuth = masked;
        } else {
            this.multifactorAuth = null;
        }
        return this;
    }
    public Account setMultifactorAuthList (List<AccountContact> mfa) {
        return setMultifactorAuth(empty(mfa) ? null : mfa.toArray(new AccountContact[0]));
    }

    @Transient @Getter @Setter private transient String remoteHost;
    @Transient @JsonIgnore @Getter @Setter private transient Boolean verifyContact;

    public ValidationResult validateEmail() { return validateEmail(getEmail()); }

    public static ValidationResult validateEmail(String email) {
        final ValidationResult result = new ValidationResult();
        if (empty(email)) {
            result.addViolation("err.email.required");
        } else {
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                result.addViolation("err.email.invalid");
            } else if (email.length() > EMAIL_MAX_LENGTH) {
                result.addViolation("err.email.tooLong");
            }
        }
        return result;
    }
}
