/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.passthru;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.FilterMatchDecision;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class TlsPassthruRuleDriver extends AbstractAppRuleDriver {

    private TlsPassthruConfig passthruConfig;

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher, Account account, Device device) {
        super.init(config, userConfig, rule, matcher, account, device);
        passthruConfig = json(json(config), TlsPassthruConfig.class);
    }

    @Override public FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final String fqdn = filter.getFqdn();
        if (passthruConfig.isPassthru(fqdn)) {
            if (log.isDebugEnabled()) log.debug("preprocess: returning pass_thru for fqdn="+fqdn);
            return FilterMatchDecision.pass_thru;
        }
        if (log.isDebugEnabled()) log.debug("preprocess: returning no_match for fqdn="+fqdn);
        return FilterMatchDecision.no_match;
    }

}
