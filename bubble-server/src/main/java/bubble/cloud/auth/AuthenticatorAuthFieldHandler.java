/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.auth;

import bubble.model.account.TotpBean;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Collections;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.json.JsonUtil.json;

public class AuthenticatorAuthFieldHandler implements AuthFieldHandler {

    @Override public List<ConstraintViolationBean> validate(String val) {
        // just ensure it is a valid TotpBean. should always be valid
        try {
            final TotpBean bean = json(val, TotpBean.class);
        } catch (Exception e) {
            return new SingletonList<>(new ConstraintViolationBean("err.authenticator.invalid", "Not a valid TotpBean: "+val+": "+ shortError(e)));
        }
        return Collections.emptyList();
    }

    public static final String MASKED_VALUE = "{\"masked\": true}";

    // we mask with a special value to tell the frontend it has been masked
    @Override public String mask(String val) { return MASKED_VALUE; }

}
