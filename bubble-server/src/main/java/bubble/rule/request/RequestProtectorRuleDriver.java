/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.request;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.rule.AbstractAppRuleDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.json.JsonUtil;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RequestProtectorRuleDriver extends AbstractAppRuleDriver {

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) RequestProtectorConfig.class; }

    @Override public Set<String> getPrimedResponseHeaderModifiers() {
        final RequestProtectorConfig config = getRuleConfig();
        return config.getHeaderReplacements().stream().map(JsonUtil::json).collect(Collectors.toSet());
    }

    @Override public void init(JsonNode config, JsonNode userConfig, BubbleApp app, AppRule rule, AppMatcher matcher,
                               Account account, Device device) {
        super.init(config, userConfig, app, rule, matcher, account, device);

        // refresh list
        final RequestProtectorConfig ruleConfig = getRuleConfig();
        ruleConfig.getHeaderReplacements();
    }
}
