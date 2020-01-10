package bubble.model.cloud;

import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.model.BubbleTags;
import bubble.model.HasBubbleTags;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.account.HasNetwork;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(baseURI=EP_NETWORKS, listFields={"name", "domain", "description", "account", "enabled"})
@ECTypeChildren(uriPrefix=EP_NETWORKS+"/{BubbleNetwork.name}", value={
        @ECTypeChild(type=BubbleNode.class, backref="network")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"account", "name"}) })
public class BubbleNetwork extends IdentifiableBase implements HasNetwork, HasBubbleTags<BubbleNetwork> {

    public static final String[] UPDATE_FIELDS = {"footprint", "description", "locale", "timezone", "plan", "state"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "domain");

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

    @ECSearchable @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
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

    @ECSearchable(filter=true) @ECField(index=40)
    @ECIndex @Column(nullable=false, updatable=false, length=DOMAIN_NAME_MAXLEN)
    @Getter @Setter private String domainName;  // denormalized from BubbleNetwork

    @Transient @JsonIgnore public String getNetworkDomain () { return name + "." + domainName; }

    @ECSearchable @ECField(index=50)
    @ECForeignKey(entity=AccountSshKey.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String sshKey;
    public boolean hasSshKey () { return !empty(sshKey); }

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private ComputeNodeSizeType computeSizeType;

    @ECSearchable @ECField(index=70)
    @ECForeignKey(entity=BubbleFootprint.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String footprint;
    public boolean hasFootprint () { return footprint != null; }

    @ECSearchable @ECField(index=80)
    @ECForeignKey(entity=CloudService.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String storage;

    @ECSearchable(filter=true) @ECField(index=90)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable @ECField(type=EntityFieldType.locale, index=100)
    @Size(max=20, message="err.locale.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(20+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String locale = getDEFAULT_LOCALE();
    public boolean hasLocale () { return !empty(locale); }

    // A unicode timezone alias from: cobbzilla-utils/src/main/resources/org/cobbzilla/util/time/unicode-timezones.xml
    // All unicode aliases are guaranteed to map to a Linux timezone and a Java timezone
    @ECSearchable @ECField(type=EntityFieldType.time_zone, index=110)
    @Size(max=100, message="err.timezone.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String timezone = "America/New_York";

    @Embedded @Getter @Setter private BubbleTags tags;

    @Transient @Getter @Setter private transient String forkHost;
    public boolean fork() { return forkHost != null; }

    @ECSearchable @ECField(index=120)
    @Column(length=20)
    @Enumerated(EnumType.STRING) @Getter @Setter private BubbleNetworkState state = created;

    public String hostFromFqdn(String fqdn) {
        if (fqdn.endsWith("."+getNetworkDomain())) {
            return fqdn.substring(0, fqdn.length() - ("."+getNetworkDomain()).length());
        }
        return die("hostFromFqdn("+fqdn+"): expected suffix ."+getNetworkDomain());
    }
}
