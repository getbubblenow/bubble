/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static bubble.rule.bblock.BubbleBlockRuleDriver.PREFIX_APPDATA_SHOW_STATS;
import static bubble.rule.bblock.BubbleBlockRuleDriver.fqdnFromKey;

@NoArgsConstructor @Accessors(chain=true)
public class BubbleFqdnBlockStats {

    @Getter @Setter private String id;
    @Getter @Setter private String fqdn;
    @Getter @Setter private boolean showBlockStats;

    public BubbleFqdnBlockStats(String fqdn, boolean showBlockStats) {
        setFqdn(fqdn);
        setShowBlockStats(showBlockStats);
    }

    public BubbleFqdnBlockStats(AppData datum) {
        final String key = datum.getKey();
        setId(datum.getUuid());
        setFqdn(fqdnFromKey(key));
        setShowBlockStats(Boolean.parseBoolean(datum.getData()));
    }

    public AppData toAppData(Account account, BubbleApp app, AppMatcher matcher, AppSite site, boolean enabled) {
        return new AppData()
                .setAccount(account.getUuid())
                .setApp(app.getUuid())
                .setMatcher(matcher.getUuid())
                .setSite(site.getUuid())
                .setKey(PREFIX_APPDATA_SHOW_STATS + fqdn)
                .setData(Boolean.toString(enabled));
    }

}
