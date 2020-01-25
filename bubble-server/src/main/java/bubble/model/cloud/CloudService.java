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
import bubble.model.boot.CloudServiceConfig;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.filters.Scrubbable;
import org.cobbzilla.wizard.filters.ScrubbableField;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.EP_CLOUDS;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_CLOUDS, listFields={"name", "description", "account", "enabled"})
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

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;
    @JsonIgnore public boolean isLocalStorage () { return name != null && name.equals(LOCAL_STORAGE); }
    @JsonIgnore public boolean isNotLocalStorage () { return !isLocalStorage(); }

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true) @ECField(index=30)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable @ECField(index=40)
    @HasValue(message="err.type.required")
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private CloudServiceType type;

    @ECSearchable @ECField(index=50) @Column(nullable=false)
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return bool(template); }

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }
    public boolean disabled () { return !enabled(); }

    @ECSearchable @ECField(index=80, type=EntityFieldType.reference)
    @ECIndex @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String delegated;
    public boolean delegated() { return delegated != null; }

    @ECSearchable @ECField(index=90)
    @HasValue(message="err.driverClass.required")
    @Column(nullable=false, length=1000)
    @Getter @Setter private String driverClass;

    @JsonIgnore @Getter(lazy=true) private final Map<Class, Boolean> _driverUsesCache = new ConcurrentHashMap<>();

    public boolean usesDriver(Class<? extends CloudServiceDriver> clazz) {
        return get_driverUsesCache().computeIfAbsent(clazz, c -> {
            try {
                return c != null && (c.getName().equals(getDriverClass()) || c.isAssignableFrom(forName(getDriverClass())));
            } catch (Exception e) {
                log.warn("usesDriver("+c.getName()+"): returning false: "+e);
                return false;
            }
        });
    }

    @ECSearchable @ECField(index=100)
    @Size(max=100000, message="err.driverConfigJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String driverConfigJson;
    public boolean hasDriverConfig () { return !empty(driverConfigJson); }

    @Transient public JsonNode getDriverConfig () { return json(driverConfigJson, JsonNode.class); }
    public CloudService setDriverConfig (JsonNode node) { return setDriverConfigJson(node == null ? null : json(node)); }

    @Size(max=10000, message="err.credentialsJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String credentialsJson;

    @Transient public CloudCredentials getCredentials () { return credentialsJson == null ? null : json(credentialsJson, CloudCredentials.class); }
    public CloudService setCredentials (CloudCredentials credentials) { return setCredentialsJson(credentials == null ? null : json(credentials)); }
    public boolean hasCredentials () {
        final CloudCredentials creds = getCredentials();
        return creds != null && !empty(creds.getParams());
    }

    @Transient @JsonIgnore @Getter(lazy=true) private final CloudServiceDriver driver = initDriver();
    private CloudServiceDriver initDriver () {
        final CloudServiceDriver d = instantiate(getDriverClass());
        d.setConfig(json(getDriverConfigJson(), JsonNode.class), this);
        d.setCredentials(getCredentials());
        return d;
    }

    @Transient @JsonIgnore public CloudServiceDriver getConfiguredDriver (BubbleConfiguration configuration) {
        switch (getType()) {
            case compute:       return getComputeDriver(configuration);
            case email: case sms:
            case authenticator: return getAuthenticationDriver(configuration);
            case geoLocation:   return getGeoLocateDriver(configuration);
            case geoCode:       return getGeoCodeDriver(configuration);
            case geoTime:       return getGeoTimeDriver(configuration);
            case dns:           return getDnsDriver(configuration);
            case storage:       return getStorageDriver(configuration);
            case payment:       return getPaymentDriver(configuration);
            default:
                log.warn("getConfiguredDriver: unrecognized type: "+getType());
            case local:
                return wireAndSetup(configuration);
        }
    }

    @Transient @JsonIgnore public ComputeServiceDriver getComputeDriver (BubbleConfiguration configuration) {
        return wireAndSetup(configuration);
    }

    @Transient @JsonIgnore public AuthenticationDriver getAuthenticationDriver(BubbleConfiguration configuration) {
        if (!getType().isAuthenticationType()) return die("getAuthenticationDriver: not an authentication type: "+getType());
        return wireAndSetup(configuration);
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
        if (isNotLocalStorage()) {
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

    private static final Map<String, CloudServiceDriver> driverCache = new ExpirationMap<>();
    public static void flushDriverCache() { driverCache.clear(); }

    public static void clearDriverCache (String uuid) { driverCache.remove(uuid); }

    public <T extends CloudServiceDriver> T wireAndSetup (BubbleConfiguration configuration) {
        // note: CloudServiceDAO calls clearDriverCache when driver config is updated,
        // then the updated class/config/credentials will be used.
        if (!hasUuid() || configuration.testMode()) {
            // this is a test before creation (or we are in test mode), just wire it up, but do not cache the result
            return _wireAndSetup(configuration);
        }
        return (T) driverCache.computeIfAbsent(getUuid(), k -> _wireAndSetup(configuration));
    }

    private <T extends CloudServiceDriver> T _wireAndSetup (BubbleConfiguration configuration) {
        final T driver;
        if (delegated()) {
            if (type.hasDelegateDriverClass()) {
                driver = (T) configuration.autowire(instantiate(type.getDelegateDriverClass(), this));
            } else {
                return die("wireAndSetup: cloud service type " + type + " does not support delegation: class not found: "+type.getDelegateDriverClassName());
            }
        } else {
            driver = (T) configuration.autowire(getDriver());
            driver.postSetup();
        }
        return driver;
    }

    public CloudService configure(CloudServiceConfig config, ValidationResult errors) {
        if (config.hasConfig()) {
            final JsonNode driverConfig = getDriverConfig();
            for (Map.Entry<String, String> cfg : config.getConfig().entrySet()) {
                final String name = cfg.getKey();
                if (driverConfig == null || !driverConfig.has(name)) {
                    errors.addViolation("err.cloud.noSuchField", "driver config field does not exist: "+name, name);
                } else if (errors.isValid()) {
                    final JsonNodeType nodeType = driverConfig.get(name).getNodeType();
                    switch (nodeType) {
                        case ARRAY: case OBJECT:
                            ((ObjectNode) driverConfig).replace(name, json(cfg.getValue(), JsonNode.class)); break;
                        case STRING:
                            ((ObjectNode) driverConfig).put(name, cfg.getValue()); break;
                        case NUMBER:
                            ((ObjectNode) driverConfig).put(name, big(cfg.getValue())); break;
                        case BOOLEAN:
                            ((ObjectNode) driverConfig).put(name, Boolean.valueOf(cfg.getValue())); break;
                        default:
                            errors.addViolation("err.cloud.invalidFieldType", "Cannot set driver config field '"+name+"' of type "+nodeType, name);
                    }
                }
            }
            setDriverConfig(driverConfig);
        }
        if (config.hasCredentials()) {
            setCredentials(config.getCredentialsObject());
        } else {
            setCredentials(null);
        }
        return this;
    }

    public CloudServiceConfig toCloudConfig() {
        final CloudServiceConfig config = new CloudServiceConfig();
        final JsonNode driverConfig = getDriverConfig();
        if (driverConfig != null) {
            for (Iterator<String> iter = driverConfig.fieldNames(); iter.hasNext(); ) {
                final String field = iter.next();
                config.addConfig(field, driverConfig.get(field).textValue());
            }
        }
        if (hasCredentials()) {
            config.setCredentials(NameAndValue.toMap(getCredentials().getParams()));
        }
        return config;
    }

    @Transient @JsonIgnore @Getter @Setter private Object testArg = null;
    @Transient @JsonIgnore @Getter @Setter private Boolean skipTest = false;
    public boolean skipTest () { return bool(skipTest); };

    public static ValidationResult testDriver(CloudService cloud, BubbleConfiguration configuration) {
        return testDriver(cloud, configuration, new ValidationResult());
    }

    public static ValidationResult testDriver(CloudService cloud, BubbleConfiguration configuration, ValidationResult errors) {
        if (cloud.skipTest()) return errors;

        final String prefix = cloud.getName()+": ";
        final Object arg = cloud.getTestArg();
        final String argString = arg != null ? " with arg=" + arg : "";
        final String invalidValue = arg == null ? null : arg.toString();
        final String driverClass = cloud.getDriverClass();
        final String errTestFailed = "err."+cloud.getType()+".testFailed";
        final String errException = "err."+cloud.getType()+".unknownError";

        final CloudServiceDriver driver;
        try {
            driver = cloud.getConfiguredDriver(configuration);
        } catch (SimpleViolationException e) {
            return errors.addViolation(e.getBean());

        } catch (Exception e) {
            return errors.addViolation(errTestFailed, prefix+"driver initialization failed: "+driverClass+": "+shortError(e));
        }
        try {
            if (!driver.test(arg)) {
                return errors.addViolation(errTestFailed, prefix+"test failed for driver: "+driverClass+argString, invalidValue);
            }
        } catch (SimpleViolationException e) {
            return errors.addViolation(e.getBean());
        } catch (Exception e) {
            return errors.addViolation(errException, prefix+"test failed for driver: "+driverClass+argString+": "+shortError(e), invalidValue);
        }
        return errors;
    }
}
