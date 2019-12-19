package bubble.cloud.auth;

import org.cobbzilla.util.string.ValidationRegexes;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Collections;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class EmailAuthFieldHandler implements AuthFieldHandler {

    @Override public List<ConstraintViolationBean> validate(String val) {
        if (empty(val)) {
            return singleError("err.email.required", "empty email address");
        }
        if (!ValidationRegexes.EMAIL_PATTERN.matcher(val).matches()) {
            return singleError("err.email.invalid", "invalid email address: "+val, val);
        }
        return Collections.emptyList();
    }

    public static final int UNMASKED_LEN = 5;

    @Override public String mask(String val) {
        final int atPos = val.indexOf("@");
        if (atPos == -1 || atPos == val.length()-1) return "*".repeat(val.length());

        String namePart = val.substring(0, atPos);
        if (namePart.length() > UNMASKED_LEN) {
            namePart = namePart.substring(0, UNMASKED_LEN) + "*".repeat(namePart.length() - UNMASKED_LEN);
        } else if (namePart.length() == 1) {
            namePart = "**";
        } else {
            namePart = namePart.charAt(0)+"*".repeat(namePart.length());
        }

        String hostPart = val.substring(atPos+1);
        final int lastDot = hostPart.lastIndexOf(".");
        if (hostPart.length() <= UNMASKED_LEN || lastDot == -1 || lastDot == hostPart.length()-1) {
            hostPart = "*".repeat(hostPart.length());
        } else {
            final String tld = hostPart.substring(lastDot + 1);
            final String host = hostPart.substring(0, lastDot);
            if (host.length() > UNMASKED_LEN) {
                hostPart = hostPart.substring(0, UNMASKED_LEN) + "*".repeat(hostPart.length() - UNMASKED_LEN) + "." + tld;
            } else {
                hostPart = "*".repeat(host.length()) + "." + tld;
            }
        }
        return namePart + "@" + hostPart;
    }

}
