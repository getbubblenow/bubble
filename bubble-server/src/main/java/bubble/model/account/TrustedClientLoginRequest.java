/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.HasValue;

import javax.validation.constraints.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.string.ValidationRegexes.UUID_REGEX;

@Slf4j @Accessors(chain=true)
public class TrustedClientLoginRequest {

    @HasValue(message="err.email.required")
    @Getter @Setter private String email;
    public boolean hasEmail () { return !empty(email); }

    public String getName () { return getEmail(); }
    public TrustedClientLoginRequest setName (String name) { return setEmail(name); }

    @Getter @Setter @HasValue(message="err.password.required")
    private String password;
    public boolean hasPassword () { return !empty(password); }

    // require timestamp to begin with a '1'.
    // note: this means this pattern will break on October 11, 2603
    private static final String TRUST_HASH_REGEX = "^1[\\d]{10}-"+UUID_REGEX+"-"+UUID_REGEX+"$";

    @HasValue(message="err.trustHash.required")
    @Pattern(regexp=TRUST_HASH_REGEX, message="err.trustHash.invalid")
    @Getter @Setter private String trustHash;

    private static final String TRUST_SALT_REGEX = "^"+UUID_REGEX+"$";

    @HasValue(message="err.trustSalt.required")
    @Pattern(regexp=TRUST_SALT_REGEX, message="err.trustHash.invalid")
    @Getter @Setter private String trustSalt;

    @JsonIgnore @Getter(lazy=true) private final long time = initTime();
    private long initTime () {
        final int firstHyphen = empty(trustSalt) ? -1 : trustSalt.indexOf('-');
        if (firstHyphen <= 11) return 0;
        try {
            return Long.parseLong(trustSalt.substring(0, firstHyphen));
        } catch (Exception e) {
            log.error("getTime: "+shortError(e));
            return 0;
        }
    }

}
