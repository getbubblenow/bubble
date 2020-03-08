/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.passthru;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.FilterMatchDecision;
import bubble.service.stream.AppRuleHarness;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

@Slf4j
public class TlsPassthruRuleDriver extends AbstractAppRuleDriver {

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) TlsPassthruConfig.class; }

    @Override public FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        final String fqdn = filter.getFqdn();
        if (passthruConfig.isPassthru(fqdn)) {
            if (log.isDebugEnabled()) log.debug("preprocess: returning pass_thru for fqdn="+fqdn);
            return FilterMatchDecision.pass_thru;
        }
        if (log.isDebugEnabled()) log.debug("preprocess: returning no_match for fqdn="+fqdn);
        return FilterMatchDecision.no_match;
    }

}
