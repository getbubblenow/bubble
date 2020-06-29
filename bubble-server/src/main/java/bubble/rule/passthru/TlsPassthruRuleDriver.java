/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.rule.AbstractAppRuleDriver;
import bubble.service.stream.AppRuleHarness;
import bubble.service.stream.ConnectionCheckResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TlsPassthruRuleDriver extends AbstractAppRuleDriver {

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) TlsPassthruConfig.class; }

    @Override public ConnectionCheckResponse checkConnection(AppRuleHarness harness, Account account, Device device, String addr, String fqdn) {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        if (passthruConfig.isPassthru(fqdn) || passthruConfig.isPassthru(addr)) {
            if (log.isDebugEnabled()) log.debug("checkConnection: returning passthru for fqdn/addr="+fqdn+"/"+addr);
            return ConnectionCheckResponse.passthru;
        }
        if (log.isDebugEnabled()) log.debug("checkConnection: returning noop for fqdn/addr="+fqdn+"/"+addr);
        return ConnectionCheckResponse.noop;
    }

}
