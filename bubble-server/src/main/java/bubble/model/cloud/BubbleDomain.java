package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.EP_DOMAINS;
import static bubble.model.cloud.AnsibleRole.sameRoleName;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.dns.DnsType.NS;
import static org.cobbzilla.util.dns.DnsType.SOA;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_DOMAINS, listFields={"name", "description", "account", "enabled"})
@ECTypeFields(list={"name", "description", "account", "enabled"})
@ECTypeChildren(uriPrefix=EP_DOMAINS+"/{BubbleDomain.name}", value={
        @ECTypeChild(type=BubbleNetwork.class, backref="domain"),
        @ECTypeChild(type=BubbleNode.class, backref="domain")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class BubbleDomain extends IdentifiableBase implements AccountTemplate {

    public static final String[] UPDATE_FIELDS = {"description", "template", "enabled", "publicDns"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS,
            "name", "rolesJson");

    public static final int DOMAIN_NAME_MAXLEN = 200;

    public BubbleDomain (BubbleDomain other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=DOMAIN_NAME_MAXLEN)
    @Getter @Setter private String name;

    public String ensureDomainSuffix(String fqdn) { return fqdn.endsWith("." + getName()) ? fqdn : fqdn + "." + getName(); }

    public String dropDomainSuffix(String fqdn) {
        return !fqdn.endsWith("." + getName()) ? fqdn
                : fqdn.substring(0, fqdn.length() - getName().length() - 1);
    }

    @ECSearchable(filter=true)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return template != null && template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable
    @ECIndex @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String delegated;
    public boolean delegated() { return delegated != null; }

    @ECSearchable @ECForeignKey(entity=CloudService.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String publicDns;

    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+") NOT NULL")
    @JsonIgnore @Getter @Setter private String rolesJson;
    public boolean hasRoles () { return !empty(getRoles()); }

    @Transient public String[] getRoles () { return rolesJson == null ? null : json(rolesJson, String[].class); }
    public BubbleDomain setRoles (String[] roles) { return setRolesJson(roles == null ? null : json(roles)); }

    public String findRole(String r) { return AnsibleRole.findRole(getRoles(), r); }

    public BubbleDomain addRole(String r) {
        final String[] roles = getRoles();
        if (roles != null) {
            if (Arrays.stream(roles).noneMatch(role -> sameRoleName(role, r))) {
                return setRoles(ArrayUtil.append(roles, r));
            } else {
                log.warn("addRole("+r+"): role already exists in domain "+getName());
                return this;
            }
        } else {
            return setRoles(new String[]{r});
        }
    }

    public BubbleDomain removeRole(String r) {
        final String[] roles = getRoles();
        if (roles == null) return null;

        final List<String> newRoles = Arrays.stream(roles).filter(role -> !sameRoleName(role, r)).collect(Collectors.toList());
        return setRolesJson(json(newRoles));
    }

    public BubbleDomain updateRole(String previous, String current) {
        final String[] roles = getRoles();
        if (roles == null) return die("updateRole: no roles!");
        final String role = findRole(previous);
        if (role != null) {
            log.debug("updateRole: removing previous role: "+role);
            removeRole(role);
        }
        log.debug("updateRole: adding new role: "+current);
        return addRole(current);
    }

    public String networkFromFqdn(String fqdn) {
        if (fqdn.endsWith("."+getName())) {
            final String prefix = fqdn.substring(0, fqdn.length() - ("." + getName()).length());
            final int lastDot = prefix.lastIndexOf('.');
            return lastDot == -1 ? prefix : prefix.substring(lastDot+1);
        }
        return die("networkFromFqdn("+fqdn+"): expected suffix ."+getName());
    }

    public DnsRecordMatch matchSOA() {
        return (DnsRecordMatch) new DnsRecordMatch().setType(SOA).setFqdn(getName());
    }

    public DnsRecordMatch matchNS() {
        return (DnsRecordMatch) new DnsRecordMatch().setType(NS).setFqdn(getName());
    }
}
