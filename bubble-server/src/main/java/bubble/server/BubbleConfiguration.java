/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.server;

import bubble.ApiConstants;
import bubble.BubbleHandlebars;
import bubble.auth.PromoCodePolicy;
import bubble.client.BubbleApiClient;
import bubble.cloud.CloudServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.server.listener.BubbleFirstTimeListener;
import bubble.service.boot.ActivationService;
import bubble.service.boot.StandardSelfNodeService;
import bubble.service.notify.LocalNotificationStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.DefaultedMap;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.FilenameRegexFilter;
import org.cobbzilla.util.io.JarLister;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.cache.redis.HasRedisConfiguration;
import org.cobbzilla.wizard.cache.redis.RedisConfiguration;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.server.RestServerHarness;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.LegalInfo;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.cobbzilla.wizard.server.config.RecaptchaConfig;
import org.cobbzilla.wizard.util.ClasspathScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.beans.Transient;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.cloud.CloudServiceDriver.CLOUD_DRIVER_PACKAGE;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.server.BubbleServer.getConfigurationSource;
import static java.util.Collections.emptyMap;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.registerUtilityHelpers;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@Configuration @NoArgsConstructor @Slf4j
public class BubbleConfiguration extends PgRestServerConfiguration
        implements HasDatabaseConfiguration, HasRedisConfiguration, HasHandlebars {

    public static final String SPRING_DB_FILTER = "classpath:/spring-db-filter.xml";

    public static final String ENV_DEBUG_NODE_INSTALL = "BUBBLE_DEBUG_NODE_INSTALL";
    public static final File DEBUG_NODE_INSTALL_FILE = new File(HOME_DIR, ".debug_node_install");

    public static final String TAG_SAGE_LAUNCHER = "sageLauncher";
    public static final String TAG_BUBBLE_NODE = "bubbleNode";
    public static final String TAG_NETWORK_UUID = "networkUuid";
    public static final String TAG_PAYMENTS_ENABLED = "paymentsEnabled";
    public static final String TAG_ENTITY_CLASSES = "entityClasses";
    public static final String TAG_LOCALES = "locales";
    public static final String TAG_CLOUD_CONFIGS = "cloudConfigs";
    public static final String TAG_LOCKED = "locked";
    public static final String TAG_SSL_PORT = "sslPort";
    public static final String TAG_PROMO_CODE_POLICY = "promoCodePolicy";
    public static final String TAG_REQUIRE_SEND_METRICS = "requireSendMetrics";

    public static final String DEFAULT_LOCAL_STORAGE_DIR = HOME_DIR + "/.bubble_local_storage";

    public BubbleConfiguration (BubbleConfiguration other) { copy(this, other); }

    @Getter @Setter private int defaultSslPort = 1443;
    @Getter @Setter private int defaultMitmProxyPort = 8888;

    @Getter @Setter private LocalNotificationStrategy localNotificationStrategy = LocalNotificationStrategy.inline;
    public LocalNotificationStrategy localNotificationStrategy() {
        return localNotificationStrategy == null ? LocalNotificationStrategy.inline : localNotificationStrategy;
    }

    @Getter @Setter private Boolean backupsEnabled = true;
    public boolean backupsEnabled() { return backupsEnabled == null || backupsEnabled; }

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
    @Setter private String localStorageDir = DEFAULT_LOCAL_STORAGE_DIR;
    public String getLocalStorageDir () { return empty(localStorageDir) ? DEFAULT_LOCAL_STORAGE_DIR : localStorageDir; }

    @Setter private File bubbleJar;
    public File getBubbleJar () {
        if (bubbleJar != null) return bubbleJar;
        try {
            final File jar = new File(BubbleServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.getName().endsWith(".jar")) {
                bubbleJar = jar;
            } else if (jar.getName().equals("classes")) {
                // Look for jar in directory above classes
                final File[] jarFile = jar.getParentFile().listFiles(new FilenameRegexFilter("bubble-server-\\d+\\.\\d+\\.\\d+[-\\w]*.jar"));
                if (jarFile == null || jarFile.length == 0) return die("no matching jar files found");
                if (jarFile.length > 1) return die("multiple matching jar files found: "+ArrayUtils.toString(jarFile));
                bubbleJar = jarFile[0];
            } else {
                return die("getBubbleJar: invalid/unsupported jar location detected: "+abs(jar));
            }
        } catch (Exception e) {
            return die("getBubbleJar: bubbleJar not set in config, and could not be determined at runtime: "+e);
        }
        return bubbleJar;
    }

    private static final AtomicReference<String> _DEFAULT_LOCALE = new AtomicReference<>();
    public static String getDEFAULT_LOCALE() {
        final String locale = _DEFAULT_LOCALE.get();
        return empty(locale) ? DEFAULT_LOCALE : locale;
    }

    @Setter private String defaultLocale = DEFAULT_LOCALE;
    public String getDefaultLocale () {
        if (!empty(defaultLocale)) {
            if (_DEFAULT_LOCALE.get() == null) _DEFAULT_LOCALE.set(defaultLocale);
            return defaultLocale;
        }
        final String[] allLocales = getAllLocales();
        if (ArrayUtils.contains(allLocales, DEFAULT_LOCALE)) {
            if (_DEFAULT_LOCALE.get() == null) _DEFAULT_LOCALE.set(DEFAULT_LOCALE);
            return DEFAULT_LOCALE;
        }
        return allLocales[0];
    }

    @Getter(lazy=true) private final String[] allLocales = initAllLocales();
    private String[] initAllLocales() {
        try {
            final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
            final Resource[] resources = resolver.getResources("classpath*:/"+MESSAGE_RESOURCE_BASE+"*");
            final String[] locales;
            if (resources.length == 0) {
                // read from jar file
                locales = new JarLister(getBubbleJar(), "^"+MESSAGE_RESOURCE_BASE+"[\\w]{2}(_[\\w]{2})?/$", name -> name.substring(MESSAGE_RESOURCE_BASE.length()).replace("/", ""))
                        .list().toArray(StringUtil.EMPTY_ARRAY);
            } else {
                locales = Arrays.stream(resources).map(Resource::getFilename).collect(Collectors.toList()).toArray(StringUtil.EMPTY_ARRAY);
            }
            final String defaultLocale = !empty(this.defaultLocale) ? this.defaultLocale : DEFAULT_LOCALE;
            if (locales.length == 0) {
                return die("initAllLocales: defaultLocale "+defaultLocale+" not found, because NO locales were found");
            }
            if (!ArrayUtils.contains(locales, defaultLocale)) {
                return die("initAllLocales: defaultLocale "+defaultLocale+" not found among locales: "+ ArrayUtils.toString(locales));
            }
            return locales;
        } catch (Exception e) {
            return die("initAllLocales: error loading locales: "+e);
        }
    }

    @Getter @Setter private LegalInfo legal = new LegalInfo();

    @Override @JsonIgnore public Handlebars getHandlebars() { return BubbleHandlebars.instance.getHandlebars(); }

    public String applyHandlebars(String val) { return HandlebarsUtil.apply(getHandlebars(), val, getEnvCtx()); }

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

    @Getter(lazy=true) private final List<String> cloudDriverClasses
            = ClasspathScanner.scan(CloudServiceDriver.class, CLOUD_DRIVER_PACKAGE).stream()
            .filter(c -> !c.getName().contains(".delegate."))
            .map(Class::getName)
            .collect(Collectors.toList());

    @Getter @Setter private PromoCodePolicy promoCodePolicy = PromoCodePolicy.disabled;
    public boolean promoCodesEnabled () { return isSageLauncher() && promoCodePolicy.enabled(); }
    public boolean promoCodesDisabled () { return !isSageLauncher() || promoCodePolicy.disabled(); }
    public boolean promoCodeRequired () { return isSageLauncher() && promoCodePolicy.required(); }

    private final AtomicReference<Map<String, Object>> publicSystemConfigs = new AtomicReference<>();
    public Map<String, Object> getPublicSystemConfigs () {
        synchronized (publicSystemConfigs) {
            if (publicSystemConfigs.get() == null) {
                final BubbleNode thisNode = getThisNode();
                final BubbleNetwork thisNetwork = getThisNetwork();
                final AccountDAO accountDAO = getBean(AccountDAO.class);
                final CloudServiceDAO cloudDAO = getBean(CloudServiceDAO.class);
                final ActivationService activationService = getBean(ActivationService.class);
                publicSystemConfigs.set(MapBuilder.build(new Object[][]{
                        {TAG_ALLOW_REGISTRATION, thisNetwork == null ? null : thisNetwork.getBooleanTag(TAG_ALLOW_REGISTRATION, false)},
                        {TAG_NETWORK_UUID, thisNetwork == null ? null : thisNetwork.getUuid()},
                        {TAG_SAGE_LAUNCHER, thisNetwork == null || isSageLauncher()},
                        {TAG_BUBBLE_NODE, thisNetwork == null ? null : thisNetwork.getInstallType() == AnsibleInstallType.node},
                        {TAG_PAYMENTS_ENABLED, cloudDAO.paymentsEnabled()},
                        {TAG_PROMO_CODE_POLICY, getPromoCodePolicy().name()},
                        {TAG_REQUIRE_SEND_METRICS, requireSendMetrics()},
                        {TAG_ENTITY_CLASSES, getSortedSimpleEntityClassMap()},
                        {TAG_LOCALES, getAllLocales()},
                        {TAG_CLOUD_CONFIGS, accountDAO.activated() ? null : activationService.getCloudDefaults()},
                        {TAG_LOCKED, accountDAO.locked()},
                        {TAG_SSL_PORT, getDefaultSslPort()}
                }));
            }
            return publicSystemConfigs.get();
        }
    }

    // called after activation, because now thisNetwork will be defined
    public void refreshPublicSystemConfigs () {
        synchronized (publicSystemConfigs) { publicSystemConfigs.set(null); }
        background(this::getPublicSystemConfigs);
    }

    public boolean paymentsEnabled () {
        final Object peValue = getPublicSystemConfigs().get(TAG_PAYMENTS_ENABLED);
        return peValue != null && Boolean.parseBoolean(peValue.toString());
    }

    @Getter @Setter private Boolean requireSendMetrics;
    public boolean requireSendMetrics () { return bool(requireSendMetrics); }

    @Getter @Setter private String[] disallowedCountries;

    public boolean hasDisallowedCountries() { return !empty(disallowedCountries); }

    public boolean isDisallowed(String country) {
        if (hasDisallowedCountries()) {
            for (String cc : getDisallowedCountries()) if (cc.equalsIgnoreCase(country)) return true;
        }
        return false;
    }

    @Getter @Setter private Boolean defaultPaymentModelsEnabled;
    public boolean defaultPaymentModelsEnabled() { return defaultPaymentModelsEnabled != null && defaultPaymentModelsEnabled; }

    @JsonIgnore @Getter(lazy=true) private final List<String> defaultCloudModels = initDefaultCloudModels();
    private List<String> initDefaultCloudModels () {
        final List<String> defaults = new ArrayList<>();
        defaults.add("models/defaults/cloudService.json");
        if (defaultPaymentModelsEnabled()) defaults.add("models/defaults/cloudService_payment.json");
        if (testMode()) defaults.addAll(getTestCloudModels());
        return defaults;
    }

    @JsonIgnore @Getter @Setter private List<String> testCloudModels = Collections.emptyList();

}
