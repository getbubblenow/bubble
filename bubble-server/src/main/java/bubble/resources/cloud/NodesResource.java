/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.List;

import static bubble.ApiConstants.EP_NODE_MANAGER;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class NodesResource extends ReadOnlyAccountOwnedResource<BubbleNode, BubbleNodeDAO> {

    private BubbleNetwork network;
    private BubbleDomain domain;

    @SuppressWarnings("unused")
    public NodesResource (Account account) { super(account); }

    @SuppressWarnings("unused")
    public NodesResource (Account account, BubbleNetwork network) {
        super(account);
        this.network = network;
    }

    @SuppressWarnings("unused")
    public NodesResource (Account account, BubbleDomain domain) {
        super(account);
        this.domain = domain;
    }

    protected List<BubbleNode> list(ContainerRequest ctx) {
        if (account != null) {
            if (network != null) {
                if (domain != null) {
                    return getDao().findByAccountAndNetworkAndDomain(getAccountUuid(ctx), network.getUuid(), domain.getUuid());
                } else {
                    return getDao().findByAccountAndNetwork(getAccountUuid(ctx), network.getUuid());
                }
            } else if (domain != null) {
                return getDao().findByAccountAndDomain(getAccountUuid(ctx), domain.getUuid());
            }
            return getDao().findByAccount(account.getUuid());
        }
        return getDao().findByAccount(getAccountUuid(ctx));
    }

    @Override protected BubbleNode find(ContainerRequest ctx, String id) {
        if (network != null) return super.find(ctx, id);
        BubbleNode node = getDao().findByUuid(id);
        if (node == null) node = super.find(ctx, id);
        if (account != null && node != null && !node.getAccount().equals(account.getUuid())) return null;
        if (domain != null && node != null && !node.getDomain().equals(domain.getUuid())) return null;
        return node;
    }

    // these should never get called
    @Override protected BubbleNode setReferences(ContainerRequest ctx, Account caller, BubbleNode node) { throw forbiddenEx(); }
    @Override protected Object daoCreate(BubbleNode nodes) { throw forbiddenEx(); }

    @Path("/{id}"+EP_NODE_MANAGER)
    public NodeManagerResource getNodeManagerResource(@Context Request req,
                                                      @Context ContainerRequest ctx,
                                                      @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        final BubbleNode node = super.find(ctx, id);
        if (node == null) throw notFoundEx(id);

        if (!caller.admin() && !caller.getUuid().equals(node.getAccount())) throw forbiddenEx();

        if (!node.hasNodeManagerPassword()) throw invalidEx("err.nodemanager.noPasswordSet");

        return configuration.subResource(NodeManagerResource.class, node);
    }

}
