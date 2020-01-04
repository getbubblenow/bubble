package bubble.model.cloud;

import bubble.client.BubbleNodeClient;
import bubble.client.BubbleNodeDownloadClient;
import bubble.client.BubbleNodeQuickClient;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.model.BubbleTags;
import bubble.model.HasBubbleTags;
import bubble.model.account.Account;
import bubble.model.account.HasNetwork;
import bubble.model.bill.BubblePlan;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.RandomUtils;
import org.cobbzilla.util.security.RsaKeyPair;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.*;
import java.io.File;
import java.util.*;

import static bubble.ApiConstants.EP_NODES;
import static bubble.model.cloud.BubbleNodeState.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.string.ValidationRegexes.IP4_MAXLEN;
import static org.cobbzilla.util.string.ValidationRegexes.IP6_MAXLEN;
import static org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKeySearchDepth.shallow;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_NODES, listFields={"name", "ip4"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"domain", "network", "host"}) })
public class BubbleNode extends IdentifiableBase implements HasNetwork, HasBubbleTags<BubbleNode> {

    public static final String TAG_INSTANCE_ID = "instance_id";
    public static final String TAG_SSH_KEY_ID = "ssh_key_id";
    public static final String TAG_ERROR = "X-Bubble-Error";

    private static final List<String> TAG_NAMES = Arrays.asList(TAG_INSTANCE_ID, TAG_SSH_KEY_ID, TAG_ERROR);

    @Override public Collection<String> validTags() { return TAG_NAMES; }

    public static BubbleNode sageMask (BubbleNode sage) {
        if (sage == null) return die("sageMask: sage was null");
        final BubbleNode sageNode = new BubbleNode();
        copy(sageNode, sage);
        sageNode.setSageNode(null)
                .setCostUnits(0)
                .setTags(null);
        return sageNode;
    }

    public static BubbleNode nodeFromFile(File file) {
        try {
            return fromJson(file, BubbleNode.class);
        } catch (Exception e) {
            return die("nodeFromFile: error parsing " + abs(file) + ": " + e);
        }
    }

    public static final String[] CREATE_FIELDS = { "domain", "network", "cloud", "region", "size", "sizeType" };

    public BubbleNode(BubbleNode request) { copy(this, request, CREATE_FIELDS); }

    @Override public void beforeCreate() {
        if (hasUuid()) return;
        super.beforeCreate();
    }

    @Override public Identifiable update(Identifiable thing) { return this; }
    public Identifiable upstreamUpdate(Identifiable thing) { copy(this, thing, null, UUID_ARRAY); return this; }

    @JsonIgnore @Transient @Override public String getName() { return getFqdn(); }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(fkDepth=shallow)
    @ECForeignKey(entity=BubbleDomain.class)
    @HasValue(message="err.network.required")
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;
    public boolean sameDomain(BubbleNode n) {
        return getDomain() != null && n.getDomain() != null && getDomain().equals(n.getDomain());
    }

    @ECSearchable(fkDepth=shallow)
    @ECForeignKey(entity=BubbleNetwork.class)
    @HasValue(message="err.network.required")
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String network;
    public boolean sameNetwork(BubbleNode n) {
        return getNetwork() != null && n.getNetwork() != null && getNetwork().equals(n.getNetwork());
    }

    @ECSearchable
    @ECForeignKey(entity=BubbleNode.class, cascade=false)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String sageNode;
    public boolean hasSageNode() { return sageNode != null; }
    public boolean selfSage() { return hasSageNode() && getSageNode().equals(getUuid()); }

    @ECSearchable(filter=true)
    @ECIndex @Column(length=250)
    @Getter @Setter private String host;
    public boolean hasHost () { return host != null; }

    @ECSearchable(filter=true)
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String fqdn;

    @ECSearchable
    @ECForeignKey(entity=CloudService.class)
    @HasValue(message="err.cloud.required")
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String cloud;

    @ECSearchable
    @HasValue(message="err.region.required")
    @ECIndex @Column(nullable=false, updatable=false, length=100)
    @Getter @Setter private String region;

    @ECSearchable
    @HasValue(message="err.size.required")
    @ECIndex @Column(nullable=false, updatable=false, length=100)
    @Getter @Setter private String size;

    @ECSearchable
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(length=20, nullable=false)
    @Getter @Setter private ComputeNodeSizeType sizeType;

    @ECSearchable
    @Column(nullable=false)
    @Getter @Setter private int costUnits;

    @Column(nullable=false)
    @Getter @Setter private int adminPort = RandomUtils.nextInt(6000, 19000);

    @Column(nullable=false)
    @Getter @Setter private int sslPort = 1443;

    @Column(length=50, nullable=false, updatable=false)
    @JsonIgnore @Getter @Setter private String ansibleUser = "root";
    @JsonIgnore @Transient public String getUser () { return getAnsibleUser(); }

    @ECSearchable(filter=true) @ECField(type=EntityFieldType.ip4)
    @ECIndex(unique=true) @Column(length=IP4_MAXLEN)
    @Getter @Setter private String ip4;
    public boolean hasIp4() { return ip4 != null; }
    public boolean localIp4() { return ip4 == null || isLocalIpv4(ip4); }

    public String id() { return getUuid() + "/fqdn=" + getFqdn() + "/ip4=" + getIp4(); }

    @ECSearchable(filter=true) @ECField(type=EntityFieldType.ip6)
    @ECIndex(unique=true) @Column(length=IP6_MAXLEN)
    @Getter @Setter private String ip6;
    public boolean hasIp6() { return ip6 != null; }

    public boolean hasSameIp(BubbleNode other) {
        return (hasIp4() && other.hasIp4() && getIp4().equals(other.getIp4()))
                || (hasIp6() && other.hasIp6() && getIp6().equals(other.getIp6()));
    }
    public boolean hasSameIp(String ip) {
        return (hasIp4() && getIp4().equals(ip)) || (hasIp6() && getIp6().equals(ip));
    }

    @ECSearchable
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(length=20, nullable=false)
    @Getter @Setter private BubbleNodeState state = created;
    @JsonIgnore @Transient public boolean isRunning () { return state == running; }
    public boolean notStopped() { return state != stopped; }

    @Embedded @Getter @Setter private BubbleTags tags;

    @Transient @JsonIgnore @Getter @Setter private transient Map<String, String> ephemeralTags;

    public boolean hasEphemeralTag (String name) { return ephemeralTags != null && ephemeralTags.containsKey(name); }

    public String getEphemeralTag(String name) { return ephemeralTags == null ? null : ephemeralTags.get(name); }

    public void addEphemeralTag (String name, String value) {
        if (ephemeralTags == null) ephemeralTags = new HashMap<>();
        ephemeralTags.put(name, value);
    }

    @Transient @JsonIgnore @Getter @Setter private transient RsaKeyPair sshKey;
    public boolean hasSshKey () { return sshKey != null; }

    @Transient @Getter @Setter private transient BubblePlan plan;
    public boolean hasPlan () { return plan != null; }

    @Transient @Getter @Setter private transient String restoreKey;
    public boolean hasRestoreKey() { return !empty(restoreKey); }

    @Transient @Getter @Setter private transient List<BubbleNode> peers;

    @Transient @Getter @Setter private transient BubbleBackup backup;

    // After a restore operation, we will want to notify the server
    @Transient @Getter @Setter private transient Boolean wasRestored;
    public boolean wasRestored() { return wasRestored != null && wasRestored; }

    public ApiClientBase getApiClient(BubbleConfiguration configuration) {
        return new BubbleNodeClient(this, configuration);
    }

    public ApiClientBase getDownloadClient(BubbleConfiguration configuration) {
        return new BubbleNodeDownloadClient(this, configuration);
    }

    public ApiClientBase getApiQuickClient(BubbleConfiguration configuration) {
        return new BubbleNodeQuickClient(this, configuration);
    }

}
