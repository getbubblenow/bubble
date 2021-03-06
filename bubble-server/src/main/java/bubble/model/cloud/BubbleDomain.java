/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.util.dns.DnsRecordMatch;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_DOMAINS;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.cobbzilla.util.dns.DnsType.NS;
import static org.cobbzilla.util.dns.DnsType.SOA;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_DOMAINS, listFields={"name", "description", "account", "enabled"})
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
public class BubbleDomain extends IdentifiableBase implements AccountTemplate, HasPriority {

    public static final String[] UPDATE_FIELDS = {"description", "template", "enabled", "priority", "publicDns"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name");

    public static final int DOMAIN_NAME_MAXLEN = 200;

    public BubbleDomain (BubbleDomain other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=DOMAIN_NAME_MAXLEN)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    public String ensureDomainSuffix(String fqdn) { return fqdn.endsWith("." + getName()) ? fqdn : fqdn + "." + getName(); }

    public String dropDomainSuffix(String fqdn) {
        return fqdn.equals(getName()) ? "" : !fqdn.endsWith("." + getName()) ? fqdn
                : fqdn.substring(0, fqdn.length() - getName().length() - 1);
    }

    @ECSearchable(filter=true) @ECField(index=30)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable @ECField(index=40)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @ECSearchable @ECField(index=60) @Column(nullable=false)
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable @ECField(index=70, type=EntityFieldType.reference)
    @ECIndex @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String delegated;
    public boolean delegated() { return delegated != null; }

    @ECSearchable @ECForeignKey(entity=CloudService.class) @ECField(index=80)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String publicDns;

    public String networkFromFqdn(String fqdn, ValidationResult errors) {
        if (!fqdn.endsWith("."+getName())) {
            errors.addViolation("err.fqdn.outOfNetwork");
            return null;
        }
        final String prefix = fqdn.substring(0, fqdn.length() - ("." + getName()).length());
        final int dotCount = countMatches(prefix, '.');
        if (dotCount != 1) {
            errors.addViolation("err.fqdn.invalid");
            return null;
        }
        final int lastDot = prefix.lastIndexOf('.');
        if (lastDot == -1) {
            errors.addViolation("err.fqdn.invalid");
            return null;
        }
        return prefix.substring(lastDot+1);
    }

    public DnsRecordMatch matchSOA() {
        return (DnsRecordMatch) new DnsRecordMatch().setType(SOA).setFqdn(getName());
    }

    public DnsRecordMatch matchNS() {
        return (DnsRecordMatch) new DnsRecordMatch().setType(NS).setFqdn(getName());
    }
}
