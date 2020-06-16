/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.CloudServiceType;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedTemplateResource;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

import static bubble.ApiConstants.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public class DomainsResourceBase extends AccountOwnedTemplateResource<BubbleDomain, BubbleDomainDAO> {

    @Autowired protected CloudServiceDAO cloudDAO;

    public DomainsResourceBase(Account account) { super(account); }

    @Override protected BubbleDomain setReferences(ContainerRequest ctx, Account caller, BubbleDomain domain) {
        final CloudService publicDnsService = cloudDAO.findByAccountAndId(caller.getUuid(), domain.getPublicDns());
        if (publicDnsService == null) throw notFoundEx(domain.getPublicDns());
        if (publicDnsService.getType() != CloudServiceType.dns) throw invalidEx("err.cloud.publicDnsNotDns");
        domain.setPublicDns(publicDnsService.getUuid());

        return super.setReferences(ctx, caller, domain);
    }

    @Path("/{id}"+EP_NETWORKS)
    public NetworksResource getNetworks(@Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        final BubbleDomain domain = find(ctx, id);
        if (domain == null) throw notFoundEx(id);
        return configuration.subResource(NetworksResource.class, caller, domain);
    }

    @Path("/{id}"+EP_NODES)
    public NodesResource getNodes(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        final BubbleDomain domain = find(ctx, id);
        if (domain == null) throw notFoundEx(id);
        return configuration.subResource(NodesResource.class, caller, domain);
    }

}
