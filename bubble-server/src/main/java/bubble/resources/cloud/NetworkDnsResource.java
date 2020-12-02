/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class NetworkDnsResource {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    private final Account account;
    private final BubbleDomain domain;
    private final BubbleNetwork network;

    public NetworkDnsResource (Account account, BubbleDomain domain, BubbleNetwork network) {
        this.account = account;
        this.domain = domain;
        this.network = network;
    }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List DNS records",
            description="List DNS records visible to the Bubble's DNS driver.",
            responses=@ApiResponse(responseCode=SC_OK, description="array of DnsRecord objects")
    )
    public Response listDns(@Context ContainerRequest ctx) {
        final DnsContext context = new DnsContext(ctx);
        return ok(context.dnsDriver.list());
    }

    @GET @Path(EP_FIND_DNS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Find DNS records",
            description="Find DNS records that match the given type and/or name (which is a regex)",
            parameters={
                    @Parameter(name="type", description="Only return records with this DNS type"),
                    @Parameter(name="name", description="Only return records whose name matches this regex")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="array of DnsRecord objects")
    )
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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Dig DNS records",
            description="Use dig to find DNS records that match name (which is a regex) and type (optional). This is used for verification - when we publish new records to our DNS provider, we want to check another (neutral, system) DNS provider to see that they are visible there too. Then we can feel more comfortable handing out that hostname to other people, who should be able to resolve it.",
            parameters={
                    @Parameter(name="type", description="Only return records with this DNS type"),
                    @Parameter(name="name", description="Only return records whose name matches this regex", required=true)
            },
            responses=@ApiResponse(responseCode=SC_OK, description="array of DnsRecord objects")
    )
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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Update a DNS record",
            description="Update a DNS record",
            responses=@ApiResponse(responseCode=SC_OK, description="the updated DnsRecord object")
    )
    public Response updateDns(@Context ContainerRequest ctx,
                              DnsRecord record) {
        final DnsContext context = new DnsContext(ctx, record);
        return ok(context.dnsDriver.update(record));
    }

    @POST @Path(EP_DELETE_DNS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Delete a DNS record",
            description="Delete a DNS record",
            responses=@ApiResponse(responseCode=SC_OK, description="the deleted DnsRecord object")
    )
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
