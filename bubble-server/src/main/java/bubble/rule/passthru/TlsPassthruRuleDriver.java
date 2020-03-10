/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.passthru;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.rule.AbstractAppRuleDriver;
import bubble.service.stream.AppRuleHarness;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TlsPassthruRuleDriver extends AbstractAppRuleDriver {

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) TlsPassthruConfig.class; }

    @Override public boolean isTlsPassthru(AppRuleHarness harness, Account account, Device device, String addr, String fqdn) {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        if (passthruConfig.isPassthru(fqdn) || passthruConfig.isPassthru(addr)) {
            if (log.isDebugEnabled()) log.debug("isTlsPassthru: returning true for fqdn/addr="+fqdn+"/"+addr);
            return true;
        }
        if (log.isDebugEnabled()) log.debug("isTlsPassthru: returning false for fqdn/addr="+fqdn+"/"+addr);
        return false;
    }

}
