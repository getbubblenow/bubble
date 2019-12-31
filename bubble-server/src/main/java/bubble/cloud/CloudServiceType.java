package bubble.cloud;

import bubble.cloud.auth.AuthFieldHandler;
import bubble.cloud.auth.AuthenticatorAuthFieldHandler;
import bubble.cloud.auth.EmailAuthFieldHandler;
import bubble.cloud.auth.SmsAuthFieldHandler;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Arrays;
import java.util.List;

import static bubble.ApiConstants.enumFromString;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

@Slf4j @NoArgsConstructor
public enum CloudServiceType {

    local,
    email (new EmailAuthFieldHandler()),
    sms (new SmsAuthFieldHandler()),
    authenticator (new AuthenticatorAuthFieldHandler()),
    dns,
    compute,
    storage,
    geoLocation,
    geoCode,
    geoTime,
    payment;

    @Getter private AuthFieldHandler authFieldHandler;

    CloudServiceType(AuthFieldHandler authFieldHandler) { this.authFieldHandler = authFieldHandler; }

    public static final List<CloudServiceType> authenticationTypes = Arrays.asList(new CloudServiceType[] { email, sms, authenticator });
    public boolean isAuthenticationType () { return authenticationTypes.contains(this); }

    public static final List<CloudServiceType> verifiableAuthenticationTypes = Arrays.asList(new CloudServiceType[] { email, sms });
    public boolean isVerifiableAuthenticationType () { return verifiableAuthenticationTypes.contains(this); }

    @Getter private String delegateDriverClassName = "bubble.cloud." + name() + ".delegate.Delegated" + capitalize(name()) + "Driver";
    @Getter(lazy=true) private final Class<? extends CloudServiceDriver> delegateDriverClass = initDelegateDriverClass();
    private Class<? extends CloudServiceDriver> initDelegateDriverClass() {
        try {
            return forName(getDelegateDriverClassName());
        } catch (Exception e) {
            log.error("Error initializing delegateDriverClass: "+e);
            return null;
        }
    }
    public boolean hasDelegateDriverClass () { return getDelegateDriverClass() != null; }

    @JsonCreator public static CloudServiceType fromString (String v) { return enumFromString(CloudServiceType.class, v); }

    public List<ConstraintViolationBean> validate(String info) {
        return authFieldHandler == null ? die("mask: no authFieldHandler for "+this) : authFieldHandler.validate(info);
    }

    public String mask(String info) {
        return authFieldHandler == null ? die("mask: no authFieldHandler for "+this) : authFieldHandler.mask(info);
    }
}
