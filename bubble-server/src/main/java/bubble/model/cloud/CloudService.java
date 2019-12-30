package bubble.model.cloud;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.dns.DnsServiceDriver;
import bubble.cloud.geoCode.GeoCodeServiceDriver;
import bubble.cloud.geoLocation.GeoLocateServiceDriver;
import bubble.cloud.geoTime.GeoTimeServiceDriver;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.storage.StorageServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.wizard.filters.Scrubbable;
import org.cobbzilla.wizard.filters.ScrubbableField;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.EP_CLOUDS;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_CLOUDS, listFields={"name", "description", "account", "enabled"})
@ECTypeFields(list={"name", "description", "account", "enabled"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECTypeChildren(uriPrefix=EP_CLOUDS+"/{CloudService.name}", value={
        @ECTypeChild(type=CloudServiceData.class, backref="cloud"),
})
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class CloudService extends IdentifiableBaseParentEntity implements AccountTemplate, Scrubbable, HasPriority {

    // do not send credentials over the wire
    public static final ScrubbableField[] SCRUB_FIELDS = {
            new ScrubbableField(CloudService.class, "credentials", CloudCredentials.class),
            new ScrubbableField(CloudService.class, "credentialsJson", String.class)
    };
    @Override public ScrubbableField[] fieldsToScrub() { return SCRUB_FIELDS; }

    public static final String[] UPDATE_FIELDS = {"description", "template", "enabled", "driverConfig", "priority"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "type", "driverClass", "credentials");

    public CloudService(CloudService other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable thing) { copy(this, thing, UPDATE_FIELDS); return this; }

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable
    @HasValue(message="err.type.required")
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private CloudServiceType type;

    @ECSearchable
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return template != null && template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }
    public boolean disabled () { return !enabled(); }

    @ECSearchable
    @ECIndex @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String delegated;
    public boolean delegated() { return delegated != null; }

    @ECSearchable
    @HasValue(message="err.driverClass.required")
    @Column(nullable=false, length=1000)
    @Getter @Setter private String driverClass;

    @ECSearchable
    @Size(max=100000, message="err.driverConfigJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String driverConfigJson;

    @Transient public JsonNode getDriverConfig () { return json(driverConfigJson, JsonNode.class); }
    public CloudService setDriverConfig (JsonNode node) { return setDriverConfigJson(node == null ? null : json(node)); }

    @Size(max=10000, message="err.credentialsJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String credentialsJson;

    @Transient public CloudCredentials getCredentials () { return credentialsJson == null ? null : json(credentialsJson, CloudCredentials.class); }
    public CloudService setCredentials (CloudCredentials credentials) { return setCredentialsJson(credentials == null ? null : json(credentials)); }

    @Transient @JsonIgnore @Getter(lazy=true) private final CloudServiceDriver driver = initDriver();
    private CloudServiceDriver initDriver () {
        final CloudServiceDriver d = instantiate(getDriverClass());
        d.setConfig(json(getDriverConfigJson(), JsonNode.class), this);
        d.setCredentials(getCredentials());
        return d;
    }

    @Transient @JsonIgnore public ComputeServiceDriver getComputeDriver (BubbleConfiguration configuration) {
        final ComputeServiceDriver compute = wireAndSetup(configuration);
        compute.startDriver();
        return compute;
    }

    @Transient @JsonIgnore public AuthenticationDriver getAuthenticationDriver(BubbleConfiguration configuration) {
        if (!getType().isAuthenticationType()) return die("getAuthenticationDriver: not an authentication type: "+getType());
        final AuthenticationDriver driver = wireAndSetup(configuration);
        driver.startDriver();
        return driver;
    }

    @Transient @JsonIgnore public RegionalServiceDriver getRegionalDriver () { return (RegionalServiceDriver) getDriver(); }

    @Transient @JsonIgnore public GeoLocateServiceDriver getGeoLocateDriver(BubbleConfiguration configuration) {
        return (GeoLocateServiceDriver) wireAndSetup(configuration);
    }
    @Transient @JsonIgnore public GeoCodeServiceDriver getGeoCodeDriver(BubbleConfiguration configuration) {
        return (GeoCodeServiceDriver) wireAndSetup(configuration);
    }
    @Transient @JsonIgnore public GeoTimeServiceDriver getGeoTimeDriver(BubbleConfiguration configuration) {
        return (GeoTimeServiceDriver) wireAndSetup(configuration);
    }
    @Transient @JsonIgnore public DnsServiceDriver getDnsDriver(BubbleConfiguration configuration) {
        return (DnsServiceDriver) wireAndSetup(configuration);
    }
    @Transient @JsonIgnore public StorageServiceDriver getStorageDriver(BubbleConfiguration configuration) {
        if (!getName().equals(LOCAL_STORAGE)) {
            final CloudCredentials credentials = getCredentials();
            final BubbleNetwork thisNetwork = configuration.getThisNetwork();
            if (thisNetwork != null && credentials.needsNewNetworkKey(thisNetwork.getUuid())) {
                log.info("getStorageDriver: initializing network-specific key for storage: " + getName() + "/" + getUuid());
                configuration.getBean(CloudServiceDAO.class).update(setCredentials(credentials.initNetworkKey(thisNetwork.getUuid())));
            }
        }
        return (StorageServiceDriver) wireAndSetup(configuration);
    }
    @Transient @JsonIgnore public PaymentServiceDriver getPaymentDriver(BubbleConfiguration configuration) {
        return (PaymentServiceDriver) wireAndSetup(configuration);
    }

    private static final Map<String, CloudServiceDriver> driverCache = new ConcurrentHashMap<>();

    private <T extends CloudServiceDriver> T wireAndSetup (BubbleConfiguration configuration) {
        // todo: CloudServiceDAO can cache these by uuid. clear cache when driver config is updated.
        synchronized (driverCache) {
            return (T) driverCache.computeIfAbsent(getUuid(), k -> {
                final T driver;
                if (delegated()) {
                    if (type.hasDelegateDriverClass()) {
                        driver = (T) configuration.autowire(instantiate(type.getDelegateDriverClass(), this));
                    } else {
                        return die("wireAndSetup: cloud service type " + type + " does not support delegation");
                    }
                } else {
                    driver = (T) configuration.autowire(getDriver());
                    driver.postSetup();
                }
                return driver;
            });
        }
    }

}
