package bubble.resources.cloud;

import bubble.dao.cloud.AnsibleRoleDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleRole;
import bubble.model.cloud.BubbleDomain;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.model.cloud.AnsibleRole.*;
import static bubble.resources.account.AccountOwnedResource.validateAccountUuid;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class DomainRolesResource {

    public Account account;
    public BubbleDomain domain;

    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private AnsibleRoleDAO roleDAO;

    public DomainRolesResource(Account account, BubbleDomain domain) {
        this.account = account;
        this.domain = domain;
    }

    @GET
    public Response list(@Context ContainerRequest ctx) {
        final DomainRoleContext drc = new DomainRoleContext(ctx);
        return ok(drc.getDomain().getRoles());
    }

    @GET @Path("/{role}")
    public Response find(@Context ContainerRequest ctx,
                         @PathParam("role") String role) {
        final DomainRoleContext drc = new DomainRoleContext(ctx, role);
        if (drc.domainRole == null) return notFound(role);
        return ok(drc.domainRole);
    }

    @PUT @Path("/{role}")
    public Response add(@Context ContainerRequest ctx,
                        @PathParam("role") String role) {

        final DomainRoleContext drc = new DomainRoleContext(ctx, role);

        // does the domain already have a role with the same name?
        if (drc.domainRole != null) {
            final SemanticVersion version = getRoleVersion(role);
            final SemanticVersion existingVersion = getRoleVersion(drc.domainRole);
            if (existingVersion == null) return die("add: role defined without version in domain: "+drc.getDomain().getUuid());
            if (existingVersion.equals(version)) {
                // same version, nothing to do
                return ok(drc.domainRoles);

            } else {
                // different version, cannot add it, it's already there
                return invalid("err.domainRole.alreadyExists", "Cannot add role "+role+" to domain "+domain.getName()+", already includes role: "+drc.domainRole);
            }
        }

        // role is not in domain's role list. add it, commit the domain
        final BubbleDomain updated = domainDAO.update(drc.getDomain().addRole(drc.role.getName()));
        return ok(updated.getRoles());
    }

    @POST @Path("/{role}")
    public Response update(@Context ContainerRequest ctx,
                           @PathParam("role") String roleName,
                           AnsibleRole role) {
        // if roleName path param has a version, use it. otherwise use the version found in the role entity
        SemanticVersion version = getRoleVersion(roleName);
        if (version != null) {
            // ensure it matches what is in the entity
            if (!role.getVersion().equals(version)) return versionMismatch(roleName, role);
        } else {
            version = role.getVersion();
            roleName = getRoleName(roleName)+"-"+version;
        }

        // roleName must match json
        if (!sameRoleName(roleName, role.getName())) return invalid("err.role.invalid", "role name mismatch", roleName);

        final DomainRoleContext drc = new DomainRoleContext(ctx, roleName, true);
        if (drc.domainRole == null) {
            log.warn("update: role not found, adding: "+roleName);
        } else {
            // if version is the same, no changes to make
            if (version.equals(getRoleVersion(drc.domainRole))) {
                log.info("update: same version, not updating");
                return ok(drc.domainRoles);
            }
        }

        final BubbleDomain updated = domainDAO.update(drc.getDomain().updateRole(roleName, role.getName()));
        log.debug("update: updated.roles="+updated.getRolesJson());
        return ok(updated.getRoles());
    }

    public Response versionMismatch(@PathParam("role") String roleName, AnsibleRole role) {
        return invalid("err.version.mismatch", "version in URL ("+roleName+") did not match version in object ("+role.getVersion()+")", roleName);
    }

    @DELETE @Path("/{role}")
    public Response remove(@Context ContainerRequest ctx,
                           @PathParam("role") String role) {

        final DomainRoleContext drc = new DomainRoleContext(ctx, role);
        if (drc.domainRole == null) return notFound(role);

        final BubbleDomain updated = domainDAO.update(drc.getDomain().removeRole(role));
        return ok(updated.getRoles());
    }

    private class DomainRoleContext {

        public Account caller;
        private BubbleDomain d;
        public BubbleDomain getDomain () { return d; }
        public AnsibleRole role;
        public String[] domainRoles;
        public String domainRole;

        public DomainRoleContext(ContainerRequest ctx) { this(ctx, null); }

        public DomainRoleContext(ContainerRequest ctx, String roleName) {
            this(ctx, roleName, false);
        }
        public DomainRoleContext(ContainerRequest ctx, String roleName, boolean okNotFound) {

            caller = userPrincipal(ctx);
            final String accountUuid = validateAccountUuid(account, ctx, caller);

            d = domainDAO.findByUuid(domain.getUuid());
            if (d == null) throw notFoundEx(domain.getName());

            if (roleName != null) {
                role = roleDAO.findByAccountAndId(accountUuid, roleName);
                if (role == null) throw notFoundEx(roleName);

                domainRoles = d.getRoles();
                domainRole = findRole(domainRoles, roleName);

                // user was requesting exact version -- ensure the domainRole version matches
                if (!okNotFound && roleName.contains("-") && !roleName.equals(domainRole)) throw notFoundEx(roleName);
            }
        }
    }
}
