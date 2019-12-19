package bubble.cloud.auth;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Collections;
import java.util.List;

public class AuthenticatorAuthFieldHandler implements AuthFieldHandler {

    @Override public List<ConstraintViolationBean> validate(String val) {
        // nothing to validate? or should we validate that the val is a proper secret key?
        return Collections.emptyList();
    }

    // return verbatim, so user can re-add if needed
    @Override public String mask(String val) { return val; }

}
