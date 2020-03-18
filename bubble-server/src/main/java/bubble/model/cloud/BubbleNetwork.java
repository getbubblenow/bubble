/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.model.BubbleTags;
import bubble.model.HasBubbleTags;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.account.HasNetwork;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static bubble.ApiConstants.EP_NETWORKS;
import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static bubble.model.cloud.BubbleDomain.DOMAIN_NAME_MAXLEN;
import static bubble.model.cloud.BubbleNetworkState.created;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTPS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.string.ValidationRegexes.HOST_PART_PATTERN;
import static org.cobbzilla.util.string.ValidationRegexes.validateRegexMatches;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(baseURI=EP_NETWORKS, listFields={"name", "domain", "description", "account", "enabled"})
@ECTypeChildren(uriPrefix=EP_NETWORKS+"/{BubbleNetwork.name}", value={
        @ECTypeChild(type=BubbleNode.class, backref="network")
})
@Entity @NoArgsConstructor @Accessors(chain=true) @ToString(of={"name", "domainName", "installType"})
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"name", "domainName"})
})
public class BubbleNetwork extends IdentifiableBase implements HasNetwork, HasBubbleTags<BubbleNetwork> {

    public static final String[] UPDATE_FIELDS = {"footprint", "description", "locale", "timezone", "state"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "domain", "sendMetrics");

    public static final String TAG_ALLOW_REGISTRATION = "allowRegistration";
    public static final String TAG_PARENT_ACCOUNT = "parentAccountUuid";

    private static final List<String> TAG_NAMES = Arrays.asList(TAG_ALLOW_REGISTRATION, TAG_PARENT_ACCOUNT);
    @Override public Collection<String> validTags() { return TAG_NAMES; }

    public BubbleNetwork (BubbleNetwork other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public void beforeCreate() {
        if (!hasUuid() || !getUuid().equals(ROOT_NETWORK_UUID)) super.beforeCreate();
    }

    @Transient @JsonIgnore public String getNetwork () { return getUuid(); }

    public static final int NETWORK_NAME_MAXLEN = 100;
    public static final int NETWORK_NAME_MINLEN = 4;

    @ECSearchable @ECField(index=10)
    @HasValue(message="err.name.required")
    @Size(min=NETWORK_NAME_MINLEN, max=NETWORK_NAME_MAXLEN, message="err.name.length")
    @ECIndex @Column(nullable=false, updatable=false, length=NETWORK_NAME_MAXLEN)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubbleDomain.class)
    @HasValue(message="err.domain.required")
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;

    @ECSearchable(filter=true) @ECField(index=40, type=EntityFieldType.fqdn)
    @ECIndex @Column(nullable=false, updatable=false, length=DOMAIN_NAME_MAXLEN)
    @Getter @Setter private String domainName;  // denormalized from BubbleDomain

    @Transient @JsonIgnore public String getNetworkDomain () { return name + "." + domainName; }

    @Column(nullable=false) @ECField(index=50)
    @Getter @Setter private Integer sslPort;

    @Transient @JsonIgnore public String getPublicUri() {
        return SCHEME_HTTPS + getNetworkDomain() + (getSslPort() == 443 ? "" : ":"+getSslPort());
    }

    public String getPublicUri(BubbleConfiguration configuration) {
        return getUuid().equals(ROOT_NETWORK_UUID) ? configuration.getPublicUriBase() : getPublicUri();
    }

    @ECIndex @Column(nullable=false, updatable=false, length=60)
    @Enumerated(EnumType.STRING)
    @Getter @Setter private AnsibleInstallType installType;

    @ECSearchable @ECField(index=70)
    @ECForeignKey(entity=AccountSshKey.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String sshKey;
    public boolean hasSshKey () { return !empty(sshKey); }

    @ECSearchable @ECField(index=80)
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private ComputeNodeSizeType computeSizeType;

    @ECSearchable @ECField(index=90)
    @ECForeignKey(entity=BubbleFootprint.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String footprint;
    public boolean hasFootprint () { return footprint != null; }

    @ECSearchable @ECField(index=100)
    @ECForeignKey(entity=CloudService.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String storage;

    @ECSearchable(filter=true) @ECField(index=110)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable @ECField(index=120)
    @Size(max=20, message="err.locale.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(20+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String locale = getDEFAULT_LOCALE();
    public boolean hasLocale () { return !empty(locale); }

    // A unicode timezone alias from: cobbzilla-utils/src/main/resources/org/cobbzilla/util/time/unicode-timezones.xml
    // All unicode aliases are guaranteed to map to a Linux timezone and a Java timezone
    @ECSearchable @ECField(index=130)
    @Size(max=100, message="err.timezone.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String timezone = "America/New_York";

    @ECSearchable @ECField(index=140)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Boolean sendMetrics = false;
    public boolean sendMetrics() { return bool(sendMetrics); }

    @Embedded @Getter @Setter private BubbleTags tags;

    @Transient @Getter @Setter private transient String forkHost;
    public boolean hasForkHost () { return !empty(forkHost); }
    public boolean fork() { return hasForkHost(); }

    @ECSearchable @ECField(index=140)
    @Column(length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private BubbleNetworkState state = created;

    public String hostFromFqdn(String fqdn) {
        if (fqdn.endsWith("."+getNetworkDomain())) {
            return fqdn.substring(0, fqdn.length() - ("."+getNetworkDomain()).length());
        }
        return die("hostFromFqdn("+fqdn+"): expected suffix ."+getNetworkDomain());
    }

    public static HostnameValidationResult validateHostname(HasNetwork request,
                                                            AccountDAO accountDAO,
                                                            BubbleNetworkDAO networkDAO) {
        HostnameValidationResult errors = new HostnameValidationResult();
        if (!request.hasName()) {
            errors.addViolation("err.name.required");
        } else {
            final String name = request.getName();
            if (!validateRegexMatches(HOST_PART_PATTERN, name)) {
                errors.addViolation("err.name.invalid");
            } else if (Account.isReservedName(name)) {
                errors.addViolation("err.name.reserved");
            } else if (name.length() > NETWORK_NAME_MAXLEN) {
                errors.addViolation("err.name.length");
            } else if (name.length() < NETWORK_NAME_MINLEN) {
                errors.addViolation("err.name.tooShort");
            } else {
                for (int i=0; i<100; i++) {
                    final String tryName = i == 0 ? name : name + i;
                    final BubbleNetwork network = networkDAO.findByNameAndDomainUuid(tryName, request.getDomain());
                    if (network != null && !network.getUuid().equals(request.getNetwork())) {
                        continue;
                    } else {
                        final Account acct = accountDAO.findByName(name);
                        if (acct != null && !acct.getUuid().equals(request.getAccount())) {
                            continue;
                        }
                    }
                    return tryName.equals(name) ? errors : errors.setSuggestedName(tryName);
                }
                errors.addViolation("err.name.alreadyInUse");
            }
        }
        return errors;
    }

}
