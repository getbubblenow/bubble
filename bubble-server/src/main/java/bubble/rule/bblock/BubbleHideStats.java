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

import static bubble.app.bblock.BubbleBlockAppConfigDriver.PREFIX_APPDATA_HIDE_STATS;
import static bubble.rule.bblock.BubbleBlockRuleDriver.fqdnFromKey;

@NoArgsConstructor @Accessors(chain=true)
public class BubbleHideStats {

    @Getter @Setter private String id;
    @Getter @Setter private String fqdn;

    public BubbleHideStats(String fqdn) {
        setFqdn(fqdn);
    }

    public BubbleHideStats(AppData datum) {
        final String key = datum.getKey();
        setId(datum.getUuid());
        setFqdn(fqdnFromKey(key));
    }

    public AppData toAppData(Account account, BubbleApp app, AppMatcher matcher, AppSite site) {
        return new AppData()
                .setAccount(account.getUuid())
                .setApp(app.getUuid())
                .setMatcher(matcher.getUuid())
                .setSite(site.getUuid())
                .setKey(PREFIX_APPDATA_HIDE_STATS +fqdn)
                .setData("true");
    }

}
