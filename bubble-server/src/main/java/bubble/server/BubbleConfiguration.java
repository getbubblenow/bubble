package bubble.server;

import bubble.ApiConstants;
import bubble.BubbleHandlebars;
import bubble.client.BubbleApiClient;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.server.listener.BubbleFirstTimeListener;
import bubble.service.boot.StandardSelfNodeService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.DefaultedMap;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.cache.redis.HasRedisConfiguration;
import org.cobbzilla.wizard.cache.redis.RedisConfiguration;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.server.RestServerHarness;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.LegalInfo;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.cobbzilla.wizard.server.config.RecaptchaConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.beans.Transient;
import java.io.File;
import java.util.Map;
import java.util.Properties;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.ApiConstants.META_PROP_BUBBLE_VERSION;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.server.BubbleServer.getConfigurationSource;
import static java.util.Collections.emptyMap;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.registerUtilityHelpers;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@Configuration @NoArgsConstructor @Slf4j
public class BubbleConfiguration extends PgRestServerConfiguration
        implements HasDatabaseConfiguration, HasRedisConfiguration, HasHandlebars {

    public static final String SPRING_DB_FILTER = "classpath:/spring-db-filter.xml";

    public static final String TAG_SAGE_LAUNCHER = "sageLauncher";
    public static final String TAG_SAGE_UUID = "sageUuid";
    public static final String TAG_PAYMENTS_ENABLED = "paymentsEnabled";

    public BubbleConfiguration (BubbleConfiguration other) { copy(this, other); }

    @Getter @Setter private int nginxPort = 1443;
    @Getter @Setter private int mitmPort = 8888;

    @Override public void registerConfigHandlerbarsHelpers(Handlebars handlebars) { registerUtilityHelpers(handlebars); }

    @Setter private RedisConfiguration redis;
    @Bean public RedisConfiguration getRedis() {
        if (redis == null) redis = new RedisConfiguration();
        if (empty(redis.getPrefix())) redis.setPrefix("bubble");
        return redis;
    }

    @Getter @Setter private Map<String, Integer> threadPoolSizes = new DefaultedMap(2);

    @Getter @Setter private RecaptchaConfig recaptcha;

    @JsonIgnore @Transient public synchronized BubbleNode getThisNode () {
        return getBean(StandardSelfNodeService.class).getThisNode();
    }
    @JsonIgnore @Transient public boolean isSelfSage () {
        final BubbleNode selfNode = getThisNode();
        return selfNode != null && selfNode.selfSage();
    }
    @JsonIgnore @Transient public boolean isSageLauncher() {
        return isSelfSage() || !hasSageNode();
    }

    @JsonIgnore @Transient public synchronized BubbleNetwork getThisNetwork () {
        return getBean(StandardSelfNodeService.class).getThisNetwork();
    }

    @JsonIgnore @Transient public synchronized BubbleNode getSageNode () {
        return getBean(StandardSelfNodeService.class).getSageNode();
    }
    public boolean hasSageNode () { return getSageNode() != null; }

    @Getter @Setter private String letsencryptEmail;
    @Getter @Setter private String localStorageDir = HOME_DIR + "/.bubble_local_storage";

    @Getter @Setter private File bubbleJar;
    @Getter @Setter private String defaultLocale = "en_US";
    @Getter @Setter private LegalInfo legal = new LegalInfo();

    @Getter @Setter private Boolean paymentsEnabled = false;
    public boolean paymentsEnabled() { return paymentsEnabled != null && paymentsEnabled; }

    @Override @JsonIgnore public Handlebars getHandlebars() { return BubbleHandlebars.instance.getHandlebars(); }

    public ApiClientBase newApiClient() { return new BubbleApiClient(new ApiConnectionInfo(getLoopbackApiBase())); }

    public String getVersion () {
        final Properties properties = new Properties();
        try {
            properties.load(loadResourceAsStream("META-INF/bubble/bubble.properties"));
        } catch (Exception e) {
            return die("getVersion: "+e, e);
        }
        return properties.getProperty(META_PROP_BUBBLE_VERSION);
    }

    @JsonIgnore public String getUnlockKey () { return BubbleFirstTimeListener.getUnlockKey(); }

    @Override @JsonIgnore public File getPgPassFile() { return new File(ApiConstants.HOME_DIR, ".BUBBLE_PG_PASSWORD"); }
    @JsonIgnore public static File getDbKeyFile() { return new File(ApiConstants.HOME_DIR, ".BUBBLE_DB_ENCRYPTION_KEY"); }

    public static File bubblePgPassFile() { return new File("/home/bubble/.BUBBLE_PG_PASSWORD"); }
    public static File bubbleDbKeyFile() { return new File("/home/bubble/.BUBBLE_DB_ENCRYPTION_KEY"); }

    public static RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> newHarness() {
        return dbFilterServer(null, null, FileUtil.toStringOrDie(bubblePgPassFile()), FileUtil.toStringOrDie(bubbleDbKeyFile()));
    }

    public static RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> dbFilterServer(String dbName,
                                                                                              String dbUser,
                                                                                              String dbPass,
                                                                                              String key) {
        final RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> harness = new RestServerHarness<>(BubbleDbFilterServer.class);
        harness.setConfigurationSource(getConfigurationSource());
        harness.init(emptyMap());
        final BubbleConfiguration c = harness.getConfiguration();
        c.setSpringContextPath(SPRING_DB_FILTER);
        if (dbName != null) c.getDatabase().setDatabaseName(dbName);
        if (dbUser != null) c.getDatabase().setUser(dbUser);
        if (dbPass != null) c.getDatabase().setPassword(dbPass);
        c.getDatabase().setEncryptionEnabled(key != null);
        c.getDatabase().setEncryptionKey(key);
        c.getDatabase().setMigrationEnabled(false);
        c.getDatabase().getPool().setMin(2);
        c.getDatabase().getPool().setMax(5);
        final BubbleDbFilterServer server = harness.getServer();
        c.setApplicationContext(server.buildSpringApplicationContext());
        return harness;
    }

    @Getter(lazy=true) private final Map<String, Object> publicSystemConfigs = MapBuilder.build(new Object[][] {
            { TAG_ALLOW_REGISTRATION, getThisNetwork().getBooleanTag(TAG_ALLOW_REGISTRATION, false) },
            { TAG_SAGE_LAUNCHER, isSageLauncher() },
            { TAG_PAYMENTS_ENABLED, paymentsEnabled() }
    });

    @Getter @Setter private String[] disallowedCountries;

    public boolean hasDisallowedCountries() { return !empty(disallowedCountries); }

    public boolean isDisallowed(String country) {
        if (hasDisallowedCountries()) {
            for (String cc : getDisallowedCountries()) if (cc.equalsIgnoreCase(country)) return true;
        }
        return false;
    }
}
