/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.util.dns.DnsType;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.dns.DnsType.A;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class NetworkDnsResource {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    private Account account;
    private BubbleDomain domain;
    private BubbleNetwork network;

    public NetworkDnsResource (Account account, BubbleDomain domain, BubbleNetwork network) {
        this.account = account;
        this.domain = domain;
        this.network = network;
    }

    @GET
    public Response listDns(@Context ContainerRequest ctx) {
        final DnsContext context = new DnsContext(ctx);
        return ok(context.dnsDriver.list());
    }

    @GET @Path(EP_FIND_DNS)
    public Response findDns(@Context ContainerRequest ctx,
                            @QueryParam("type") DnsType type,
                            @QueryParam("name") String name) {

        final DnsContext context = new DnsContext(ctx);

        final DnsRecordMatch matcher = new DnsRecordMatch().setSubdomain(network.getNetworkDomain());
        if (type != null) matcher.setType(type);
        if (name != null) matcher.setPattern(name);

        return ok(context.dnsDriver.list(matcher));
    }

    @GET @Path(EP_DIG_DNS)
    public Response digDns(@Context ContainerRequest ctx,
                           @QueryParam("type") DnsType type,
                           @QueryParam("name") String name) {

        final DnsContext context = new DnsContext(ctx);
        if (name == null) return invalid("err.name.required");

        if (!name.endsWith(network.getNetworkDomain())) name += "." + network.getNetworkDomain();
        if (type == null) type = A;

        return ok(context.dnsDriver.dig(domain, type, name));
    }

    @POST @Path(EP_UPDATE_DNS)
    public Response updateDns(@Context ContainerRequest ctx,
                              DnsRecord record) {
        final DnsContext context = new DnsContext(ctx, record);
        return ok(context.dnsDriver.update(record));
    }

    @POST @Path(EP_DELETE_DNS)
    public Response removeDns(@Context ContainerRequest ctx,
                              DnsRecord record) {
        final DnsContext context = new DnsContext(ctx, record);
        return ok(context.dnsDriver.remove(record));
    }

    class DnsContext {
        public Account caller;
        public CloudService dnsService;
        public DnsServiceDriver dnsDriver;

        public DnsContext (ContainerRequest ctx) { this(ctx, null); }

        public DnsContext (ContainerRequest ctx, DnsRecord record) {
            caller = userPrincipal(ctx);
            if (!caller.admin() && !domain.getAccount().equals(caller.getUuid())) throw forbiddenEx();

            if (record != null && !record.getFqdn().endsWith(network.getNetworkDomain())) {
                throw invalidEx("err.fqdn.outOfNetwork", "FQDN "+record.getFqdn()+" is not in network "+network.getNetworkDomain());
            }

            dnsService = cloudDAO.findByUuid(domain.getPublicDns());
            if (dnsService == null) throw notFoundEx(domain.getPublicDns());
            dnsDriver = dnsService.getDnsDriver(configuration);
        }
    }

}
