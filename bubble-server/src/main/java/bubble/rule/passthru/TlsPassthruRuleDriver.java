/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.rule.AbstractAppRuleDriver;
import bubble.service.stream.AppRuleHarness;
import bubble.service.stream.ConnectionCheckResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import java.util.Set;
import java.util.stream.Collectors;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class TlsPassthruRuleDriver extends AbstractAppRuleDriver {

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) TlsPassthruConfig.class; }

    @Override public Set<String> getPrimedFlexDomains() {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        return passthruConfig.getFlexDomains().stream()
                .filter(d -> !d.startsWith("!"))
                .collect(Collectors.toSet());
    }

    @Override public Set<String> getPrimedFlexExcludeDomains() {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        return passthruConfig.getFlexDomains().stream()
                .filter(d -> d.startsWith("!"))
                .map(d -> d.substring(1))
                .collect(Collectors.toSet());
    }

    @Override public void init(JsonNode config,
                               JsonNode userConfig,
                               BubbleApp app,
                               AppRule rule,
                               AppMatcher matcher,
                               Account account,
                               Device device) {
        super.init(config, userConfig, app, rule, matcher, account, device);

        // refresh lists
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        passthruConfig.getPassthruSet();
        passthruConfig.getFlexSet();
    }

    @Override public ConnectionCheckResponse checkConnection(AppRuleHarness harness,
                                                             Account account,
                                                             Device device,
                                                             String clientAddr,
                                                             String serverAddr,
                                                             String fqdn) {
        final TlsPassthruConfig passthruConfig = getRuleConfig();
        if (passthruConfig.isPassthru(fqdn) || passthruConfig.isPassthru(serverAddr)) {
            if (log.isDebugEnabled()) log.debug("checkConnection: detected passthru for fqdn/addr="+fqdn+"/"+ serverAddr);
            return ConnectionCheckResponse.passthru;
        }
        if (log.isDebugEnabled()) log.debug("checkConnection: returning noop for fqdn/addr="+fqdn+"/"+ serverAddr);
        return ConnectionCheckResponse.noop;
    }

    @Override public JsonNode upgradeRuleConfig(JsonNode sageRuleConfig, JsonNode localRuleConfig) {
        final TlsPassthruConfig sageConfig = json(sageRuleConfig, getConfigClass());
        final TlsPassthruConfig localConfig = json(sageRuleConfig, getConfigClass());
        if (sageConfig.hasPassthruFqdnList()) {
            for (String fqdn : sageConfig.getPassthruFqdnList()) {
                if (!localConfig.hasPassthruFqdnList() || localConfig.hasPassthruFqdn(fqdn)) {
                    localConfig.setPassthruFqdnList(ArrayUtil.append(localConfig.getPassthruFqdnList(), fqdn));
                }
            }
        }
        if (sageConfig.hasPassthruFeedList()) {
            for (TlsPassthruFeed feed : sageConfig.getPassthruFeedList()) {
                if (!localConfig.hasPassthruFeed(feed)) {
                    localConfig.setPassthruFeedList(ArrayUtil.append(localConfig.getPassthruFeedList(), feed));
                }
            }
        }
        return json(json(localConfig), JsonNode.class);
    }

}
