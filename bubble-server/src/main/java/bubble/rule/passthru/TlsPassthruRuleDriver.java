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
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import static org.cobbzilla.util.json.JsonUtil.json;

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

    @Override public JsonNode upgradeRuleConfig(JsonNode sageRuleConfig, JsonNode localRuleConfig) {
        final TlsPassthruConfig sageConfig = json(sageRuleConfig, getConfigClass());
        final TlsPassthruConfig localConfig = json(sageRuleConfig, getConfigClass());
        if (sageConfig.hasFqdnList()) {
            for (String fqdn : sageConfig.getFqdnList()) {
                if (!localConfig.hasFqdnList() || localConfig.hasFqdn(fqdn)) {
                    localConfig.setFqdnList(ArrayUtil.append(localConfig.getFqdnList(), fqdn));
                }
            }
        }
        if (sageConfig.hasFeedList()) {
            for (TlsPassthruFeed feed : sageConfig.getFeedList()) {
                if (!localConfig.hasFeed(feed)) {
                    localConfig.setFeedList(ArrayUtil.append(localConfig.getFeedList(), feed));
                }
            }
        }
        return json(json(localConfig), JsonNode.class);
    }

}
