/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.app;

import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.resources.account.AccountOwnedResource;
import lombok.Getter;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.util.List;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AppMatchersResource extends AccountOwnedResource<AppMatcher, AppMatcherDAO> {

    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppSiteDAO siteDAO;
    @Autowired protected BubbleDomainDAO domainDAO;

    @Getter private BubbleApp app;

    public AppMatchersResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

    @Override protected List<AppMatcher> list(ContainerRequest ctx) {
        return getDao().findByAccountAndApp(getAccountUuid(ctx), app.getUuid());
    }

    @Override protected AppMatcher find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), id);
    }

    @Override protected AppMatcher setReferences(ContainerRequest ctx, Account caller, AppMatcher matcher) {
        final AppRule rule = ruleDAO.findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), matcher.getRule());
        if (rule == null) throw notFoundEx(matcher.getRule());
        matcher.setRule(rule.getUuid());

        final AppSite site = siteDAO.findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), matcher.getSite());
        if (site == null) throw notFoundEx(matcher.getSite());
        matcher.setSite(site.getUuid());

        matcher.setApp(app.getUuid());

        return super.setReferences(ctx, caller, matcher);
    }
}
