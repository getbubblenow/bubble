/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble;

import bubble.model.cloud.BubbleNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.core.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.http.HttpHeaders.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.*;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class ApiConstants {

    public static final String ROOT_NETWORK_UUID = "00000000-0000-0000-0000-000000000000";

    public static final String DEFAULT_LOCALE = "en_US";

    public static final String[] ROLES_SAGE = {"common", "nginx", "bubble", "finalizer"};
    public static final String[] ROLES_NODE = {"common", "nginx", "bubble", "algo", "mitmproxy", "finalizer"};

    public static final String ANSIBLE_DIR = "ansible";
    public static final List<String> BUBBLE_SCRIPTS = splitAndTrim(stream2string(ANSIBLE_DIR + "/bubble_scripts.txt"), "\n")
            .stream().filter(s -> !empty(s)).collect(Collectors.toList());

    private static final AtomicReference<String> bubbleDefaultDomain = new AtomicReference<>();

    public static final ObjectMapper DB_JSON_MAPPER = COMPACT_MAPPER;

    private static String initDefaultDomain() {
        final File f = new File(HOME_DIR, ".BUBBLE_DEFAULT_DOMAIN");
        final String domain = toStringOrDie(f);
        return domain != null ? domain.trim() : die("initDefaultDomain: "+abs(f)+" not found");
    }

    public static String getBubbleDefaultDomain () {
        synchronized (bubbleDefaultDomain) {
            if (bubbleDefaultDomain.get() == null) bubbleDefaultDomain.set(initDefaultDomain());
        }
        return bubbleDefaultDomain.get();
    }

    public static final BubbleNode NULL_NODE = new BubbleNode() {
        @Override public String getUuid() { return "NULL_UUID"; }
    };

    public static final GoogleAuthenticator G_AUTH = new GoogleAuthenticator();

    public static final long MAX_KNOWN_HOST_KEY_TIMEOUT = MINUTES.toMillis(1);
    public static final String KNOWN_HOST_KEY_SSH_KEYSCAN_COMMAND = "ssh-keyscan -t rsa $(hostname -d) 2>&1 | grep -v \"^#\"";

    private static final AtomicReference<String> knownHostKey = new AtomicReference<>();
    public static String getKnownHostKey () {
        return lazyGet(knownHostKey,
                () -> {
                    long start = now();
                    String key = execScript(KNOWN_HOST_KEY_SSH_KEYSCAN_COMMAND).trim();
                    while (key.contains(" failure ") && now() < start + MAX_KNOWN_HOST_KEY_TIMEOUT) {
                        log.warn("getKnownHostKey: ssh-keyscan returned: "+key);
                        sleep(SECONDS.toMillis(5), "getKnownHostKey");
                        key = execScript(KNOWN_HOST_KEY_SSH_KEYSCAN_COMMAND).trim();
                    }
                    if (key.contains(" failure ")) return die("getKnownHostKey: error: ssh-keyscan returned: "+key);
                    log.info("getKnownHostKey: found key = "+key);
                    return key;
                },
                () -> die("getKnownHostKey"));
    }

    private static final AtomicReference<String> privateIp = new AtomicReference<>();
    public static String getPrivateIp() {
        return lazyGet(privateIp,
                () -> configuredIps().stream()
                        .filter(addr -> addr.startsWith("10."))
                        .findFirst()
                        .orElse(null),
                () -> {
                    final String msg = "getPrivateIp: no system private IP found, configuredIps=" + StringUtil.toString(configuredIps());
                    log.error(msg);
                    return die(msg);
                });
    }

    public static final Predicate ALWAYS_TRUE = m -> true;
    public static final String HOME_DIR;
    static {
        final String userHome = System.getProperty("user.home");
        final String envHome = System.getenv("HOME");
        if (envHome != null && !userHome.equals(envHome)) {
            log.warn("System.getProperty(\"user.home\") == "+userHome+" differs from System.getenv(\"HOME\") == "+envHome+", using HOME from environment: "+envHome);
            HOME_DIR = envHome;
        } else {
            HOME_DIR = userHome;
        }
    }

    public static final String BUBBLE_CLOUD_SERVICE_DATA = "bubble_cloudServiceData";
    public static final File CACERTS_DIR = new File(HOME_DIR, "cacerts");
    public static final File MITMPROXY_CERT_DIR = new File(HOME_DIR, "mitm_certs");

    public static final String META_PROP_BUBBLE_VERSION = "bubble.version";

    public static final String SESSION_HEADER = "X-Bubble-Session";

    public static final String MESSAGE_RESOURCE_BASE = "messages/";

    public static final String ENTITY_CONFIGS_ENDPOINT = "/ec";

    public static final String AUTH_ENDPOINT = "/auth";
    public static final String EP_ACTIVATE = "/activate";
    public static final String EP_CONFIGS = "/configs";
    public static final String EP_READY = "/ready";
    public static final String EP_REGISTER = "/register";
    public static final String EP_LOGIN = "/login";
    public static final String EP_APP_LOGIN = "/appLogin";
    public static final String EP_LOGOUT = "/logout";
    public static final String EP_TRUST = "/trust";
    public static final String EP_FORGOT_PASSWORD = "/forgotPassword";
    public static final String EP_CHANGE_PASSWORD = "/changePassword";
    public static final String EP_ERROR_API = "/errorApi";
    public static final String EP_CA_CERT = "/cacert";
    public static final String EP_KEY = "/key";
    public static final String EP_SCRIPT = "/script";
    public static final String EP_APPROVE = "/approve";
    public static final String EP_DENY = "/deny";
    public static final String EP_AUTHENTICATOR = "/authenticator";
    public static final String EP_TIME = "/time";
    public static final String EP_SUPPORT = "/support";
    public static final String EP_APP_LINKS = "/appLinks";
    public static final String EP_PATCH = "/patch";
    public static final String EP_VERIFY_KEY = "/verifyKey";

    public static final String ACCOUNTS_ENDPOINT = "/users";
    public static final String EP_POLICY = "/policy";
    public static final String EP_CONTACTS = "/contacts";
    public static final String EP_REQUEST = "/request";
    public static final String EP_DOWNLOAD = "/download";

    public static final String MESSAGES_ENDPOINT = "/messages";
    public static final String TIMEZONES_ENDPOINT = "/timezones";

    public static final String APPS_ENDPOINT = "/apps";
    public static final String DRIVERS_ENDPOINT = "/drivers";
    public static final String CLOUDS_ENDPOINT = "/clouds";
    public static final String PROXY_ENDPOINT = "/p";
    public static final String PROMOTIONS_ENDPOINT = "/promos";

    public static final String NOTIFY_ENDPOINT = "/notify";
    public static final String EP_READ_METADATA = "/meta";
    public static final String EP_READ = "/read";
    public static final String EP_LIST = "/list";
    public static final String EP_LIST_NEXT = "/listNext";
    public static final String EP_WRITE = "/write";
    public static final String EP_DELETE = "/delete";
    public static final String EP_REKEY = "/rekey";

    public static final String DOMAINS_ENDPOINT = "/domains";
    public static final String PLANS_ENDPOINT = "/plans";
    public static final String FOOTPRINTS_ENDPOINT = "/footprints";
    public static final String EP_CLEAN_BACKUPS = "/clean";
    public static final String PAYMENT_METHODS_ENDPOINT = "/paymentMethods";

    public static final String ME_ENDPOINT = "/me";
    public static final String EP_APPS = APPS_ENDPOINT;
    public static final String EP_MITM = "/mitm";
    public static final String EP_ENABLE = "/enable";
    public static final String EP_DISABLE = "/disable";
    public static final String EP_RULES = "/rules";
    public static final String EP_MATCHERS = "/matchers";
    public static final String EP_DATA = "/data";
    public static final String EP_VIEW = "/view";
    public static final String EP_CONFIG = "/config";
    public static final String EP_SITES = "/sites";
    public static final String EP_MESSAGES = MESSAGES_ENDPOINT;
    public static final String EP_DRIVERS = DRIVERS_ENDPOINT;
    public static final String EP_CLOUDS = CLOUDS_ENDPOINT;
    public static final String EP_REGIONS = "/regions";
    public static final String EP_DOMAINS = DOMAINS_ENDPOINT;
    public static final String EP_NETWORKS = "/networks";
    public static final String EP_PLANS = PLANS_ENDPOINT;
    public static final String EP_CURRENT_PLANS = "/currentPlans";
    public static final String EP_TAGS = "/tags";
    public static final String EP_NODES = "/nodes";
    public static final String EP_DEVICES = "/devices";
    public static final String EP_DEVICE_TYPES = "/deviceTypes";
    public static final String EP_DEFAULT_SECURITY_LEVEL = "/defaultSecurityLevel";
    public static final String EP_FLEX_ROUTERS = "/flexRouters";
    public static final String EP_MODEL = "/model";
    public static final String EP_VPN = "/vpn";
    public static final String EP_IPS = "/ips";
    public static final String EP_SECURITY_LEVEL = "/securityLevel";
    public static final String EP_PLAN = "/plan";
    public static final String EP_PAYMENT_METHOD = "/paymentMethod";
    public static final String EP_PAYMENT_METHODS = PAYMENT_METHODS_ENDPOINT;
    public static final String EP_PAYMENT = "/payment";
    public static final String EP_PAYMENTS = "/payments";
    public static final String EP_PAY = "/pay";
    public static final String EP_BILL = "/bill";
    public static final String EP_BILLS = "/bills";
    public static final String EP_CLOSEST = "/closest";
    public static final String EP_SENT_NOTIFICATIONS = "/notifications/outbox";
    public static final String EP_RECEIVED_NOTIFICATIONS = "/notifications/inbox";
    public static final String EP_REFERRAL_CODES = "/referralCodes";
    public static final String EP_STORAGE = "/storage";
    public static final String EP_PACKER = "/packer";
    public static final String EP_DNS = "/dns";
    public static final String EP_BACKUPS = "/backups";
    public static final String EP_FIND_DNS = "/find";
    public static final String EP_DIG_DNS = "/dig";
    public static final String EP_UPDATE_DNS = "/update";
    public static final String EP_DELETE_DNS = "/remove";
    public static final String EP_FOOTPRINTS = FOOTPRINTS_ENDPOINT;
    public static final String EP_ACTIONS = "/actions";
    public static final String EP_START = "/start";
    public static final String EP_STOP = "/stop";
    public static final String EP_RESTORE = "/restore";
    public static final String EP_KEYS = "/keys";
    public static final String EP_STATUS = "/status";
    public static final String EP_PROMOTIONS = PROMOTIONS_ENDPOINT;
    public static final String EP_FORK = "/fork";
    public static final String EP_NODE_MANAGER = "/nodeman";
    public static final String EP_UPGRADE = "/upgrade";
    public static final String EP_LOGS = "/logs";
    public static final String EP_FOLLOW = "/follow";
    public static final String EP_FOLLOW_AND_APPLY_REGEX = "/followAndApplyRegex";

    public static final String DETECT_ENDPOINT = "/detect";
    public static final String EP_LOCALE = "/locale";
    public static final String EP_TIMEZONE = "/timezone";

    public static final String ID_ENDPOINT = "/id";
    public static final String SEARCH_ENDPOINT = "/search";
    public static final String DEBUG_ENDPOINT = "/debug";
    public static final String BUBBLE_MAGIC_ENDPOINT = "/.bubble";

    public static final String OPENAPI_JSON_ENDPOINT = "/openapi.json";

    public static final String FILTER_HTTP_ENDPOINT = "/filter";
    public static final String EP_CHECK = "/check";
    public static final String EP_APPLY = "/apply";
    public static final String EP_ASSETS = "/assets";

    // requests to a first-party host with this prefix will be forwarded to bubble
    public static final String BUBBLE_FILTER_PASSTHRU = "/__bubble";

    // search constants
    public static final int MAX_SEARCH_PAGE = 50;
    public static final String Q_FILTER = "query";
    public static final String Q_META = "meta";
    public static final String Q_NOCACHE = "nocache";
    public static final String Q_PAGE = "page";
    public static final String Q_SIZE = "size";
    public static final String Q_SORT = "sort";

    // param for writing AppData via GET (see FilterHttpResource and UserBlockerStreamFilter)
    public static final String Q_DATA = "data";
    public static final String Q_REDIRECT = "redirect";

    public static final int MAX_NOTIFY_LOG = 10000;
    public static final int ERROR_MAXLEN = 20000;
    public static final int ERROR_MAX_BEFORE_ELLIPSES = 19900;

    public static final String API_TAG_ACTIVATION = "activation";
    public static final String API_TAG_AUTH = "auth";
    public static final String API_TAG_ACCOUNT = "account";
    public static final String API_TAG_ACCOUNT_OBJECTS = "account-owned objects";
    public static final String API_TAG_SEARCH = "search";
    public static final String API_TAG_BACKUP_RESTORE = "backup and restore";
    public static final String API_TAG_NODE = "node";
    public static final String API_TAG_NODE_MANAGER = "node manager";

    public static String getToken(String json) {
        if (json == null) return null;
        final JsonNode val = json(json, JsonNode.class).get("token");
        return val == null ? null : val.textValue();
    }

    @Getter(lazy=true) private static final String[] hostPrefixes = Arrays.stream(stream2string("bubble/host-prefixes.txt")
            .split("\n"))
            .filter(ZillaRuntime::notEmpty)
            .toArray(String[]::new);

    public static String newNodeHostname() {
        final String rand0 = getHostPrefixes()[RandomUtils.nextInt(0, getHostPrefixes().length)];
        final int rand1 = RandomUtils.nextInt(0, 100);
        final String rand2 = randomAlphanumeric(2).toLowerCase();
        final int rand3 = RandomUtils.nextInt(0, 10);
        final String rand4 = randomAlphanumeric(1).toLowerCase();
        return rand0+"-"+(rand1 < 10 ? "0"+rand1 : rand1)+rand2+"-"+rand3+rand4;
    }

    public static String newNetworkName() {
        final String rand0 = getHostPrefixes()[RandomUtils.nextInt(0, getHostPrefixes().length)];
        final String rand1 = randomAlphanumeric(2).toLowerCase() + RandomUtils.nextInt(10, 100) + randomAlphanumeric(1).toLowerCase();
        final String rand2 = randomAlphanumeric(2).toLowerCase() + RandomUtils.nextInt(10, 100) + randomAlphanumeric(1).toLowerCase();
        final String rand3 = randomAlphanumeric(2).toLowerCase() + RandomUtils.nextInt(10, 100) + randomAlphanumeric(1).toLowerCase();
        return rand0+"-"+rand1+"-"+rand2+"-"+rand3;
    }

    public static String getRemoteHost(Request req) {
        final String remoteHost = getRemoteAddr(req);
        if (isPublicIpv4(remoteHost)) return remoteHost;
        final String publicIp = getFirstPublicIpv4();
        if (publicIp != null) return publicIp;
        final String externalIp = getExternalIp();
        return isPublicIpv4(externalIp) ? externalIp : remoteHost;
    }

    public static String getRemoteAddr(Request req) {
        final String xff = req.getHeader("X-Forwarded-For");
        return xff == null ? req.getRemoteAddr() : xff;
    }

    public static String getUserAgent(ContainerRequest ctx) { return ctx.getHeaderString(USER_AGENT); }
    public static String getReferer(ContainerRequest ctx) { return ctx.getHeaderString(REFERER); }

    public static final String DETECT_LOCALE = "detect";
    public static final String DETECT_TIMEZONE = "detect";

    public static List<String> getLocales(ContainerRequest ctx, String defaultLocale) {
        final List<String> locales = new ArrayList<>();
        final String langHeader = ctx.getHeaderString(ACCEPT_LANGUAGE);
        if (langHeader == null) {
            locales.add(defaultLocale);
            return locales;
        }
        final String[] parts = langHeader.split(",");
        for (String part : parts) {
            final String[] subParts = part.split(",");
            for (String val : subParts) {
                locales.add(val.replaceAll("-", "_").replaceFirst(";.*", "").trim());
            }
        }
        locales.add(defaultLocale);
        return locales;
    }

    public static String normalizeLangHeader(@Context Request req) {
        String langHeader = req.getHeader(ACCEPT_LANGUAGE);
        if (langHeader == null) return null;

        // remove everything after the first comma, change hyphens to underscores
        int comma = langHeader.indexOf(',');
        if (comma != -1) langHeader = langHeader.substring(0, comma);
        return langHeader.replace('-', '_').trim();
    }

    public static boolean isHttpsPort(int sslPort) { return sslPort % 1000 == 443; }

    public static <T extends Enum> T enumFromString(Class<T> e, String v) {
        return Arrays.stream(e.getEnumConstants())
                .filter(t->t.name().equalsIgnoreCase(v))
                .findFirst()
                .orElseThrow((Supplier<RuntimeException>) () ->
                        invalidEx("err."+e.getSimpleName()+".invalid", "Invalid "+e.getSimpleName()+": "+v, v));
    }

    public static void copyScriptsOrDie(File scriptsParentDir) {
        try {
            copyScripts(scriptsParentDir);
        } catch (IOException e) {
            die("copyScriptsOrDie: "+shortError(e), e);
        }
    }

    public static void copyScripts(File scriptsParentDir) throws IOException {
        // write scripts
        final File scriptsDir = mkdirOrDie(new File(scriptsParentDir, "scripts"));
        for (String script : BUBBLE_SCRIPTS) {
            toFile(new File(scriptsDir, script), stream2string("scripts/"+script));
        }
    }
}
