/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.service.stream.AppRuleHarness;
import bubble.service.stream.ConnectionCheckResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.io.StreamUtil.stream2bytes;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public interface AppRuleDriver {

    Logger log = LoggerFactory.getLogger(AppRuleDriver.class);

    InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

    // also used in dnscrypt-proxy/plugin_reverse_resolve_cache.go
    String REDIS_REJECT_LISTS = "rejectLists";
    String REDIS_BLOCK_LISTS = "blockLists";
    String REDIS_WHITE_LISTS = "whiteLists";
    String REDIS_FILTER_LISTS = "filterLists";
    String REDIS_FLEX_LISTS = "flexLists";  // used in mitmproxy and dnscrypt-proxy for flex routing
    String REDIS_FLEX_EXCLUDE_LISTS = "flexExcludeLists";  // used in mitmproxy and dnscrypt-proxy for flex routing
    String REDIS_RESPONSE_HEADER_MODIFIER_LISTS = "responseHeaderModifierLists";  // used in mitmproxy
    String REDIS_LIST_SUFFIX = "~UNION";

    default Set<String> getPrimedRejectDomains () { return null; }
    default Set<String> getPrimedBlockDomains () { return null; }
    default Set<String> getPrimedWhiteListDomains() { return null; }
    default Set<String> getPrimedFilterDomains () { return null; }
    default Set<String> getPrimedFlexDomains () { return null; }
    default Set<String> getPrimedFlexExcludeDomains () { return null; }
    default Set<String> getPrimedResponseHeaderModifiers () { return null; }

    static void defineRedisRejectSet(RedisService redis, String ip, String list, String[] rejectDomains) {
        defineRedisSet(redis, ip, REDIS_REJECT_LISTS, list, rejectDomains);
    }

    static void defineRedisBlockSet(RedisService redis, String ip, String list, String[] fullyBlockedDomains) {
        defineRedisSet(redis, ip, REDIS_BLOCK_LISTS, list, fullyBlockedDomains);
    }

    static void defineRedisWhiteListSet(RedisService redis, String ip, String list, String[] fullyBlockedDomains) {
        defineRedisSet(redis, ip, REDIS_WHITE_LISTS, list, fullyBlockedDomains);
    }

    static void defineRedisFilterSet(RedisService redis, String ip, String list, String[] filterDomains) {
        defineRedisSet(redis, ip, REDIS_FILTER_LISTS, list, filterDomains);
    }

    static void defineRedisFlexSet(RedisService redis, String ip, String list, String[] flexDomains) {
        defineRedisSet(redis, ip, REDIS_FLEX_LISTS, list, flexDomains);
    }

    static void defineRedisFlexExcludeSet(RedisService redis, String ip, String list, String[] flexExcludeDomains) {
        defineRedisSet(redis, ip, REDIS_FLEX_EXCLUDE_LISTS, list, flexExcludeDomains);
    }

    static void defineRedisResponseHeaderModifiersSet(RedisService redis, String ip, String list,
                                                      String[] modifiers) {
        defineRedisSet(redis, ip, REDIS_RESPONSE_HEADER_MODIFIER_LISTS, list, modifiers);
    }

    /**
     * `settings` parameter may be list of domains or any other list of strings - i.e. list of JSONs with specific setup
     * for the prime option of the driver.
     */
    static void defineRedisSet(RedisService redis, String ip, String listOfListsName, String listName,
                               String[] settings) {
        final String listOfListsForIp = listOfListsName + "~" + ip;
        final String unionSetName = listOfListsForIp + REDIS_LIST_SUFFIX;
        final String ipList = listOfListsForIp + "~" + listName;
        final String tempList = ipList + "~"+now()+randomAlphanumeric(5);
        redis.sadd_plaintext(tempList, settings);
        redis.rename(tempList, ipList);
        redis.sadd_plaintext(listOfListsForIp, ipList);
        final Long count = redis.sunionstore(unionSetName, redis.smembers(listOfListsForIp));
        if (log.isDebugEnabled()) log.debug("defineRedisSet("+ip+","+listOfListsName+","+listName+"): unionSetName="+unionSetName+" size="+count);
    }

    static boolean isFlexRouteFqdn(RedisService redis, String ip, String[] fqdns) {
        for (String fqdn : fqdns) {
            if (isFlexRouteFqdn(redis, ip, fqdn)) return true;
        }
        return false;
    }

    static boolean isFlexRouteFqdn(RedisService redis, String ip, String fqdn) {

        final String excludeKey = REDIS_FLEX_EXCLUDE_LISTS + "~" + ip + REDIS_LIST_SUFFIX;
        if (redis.sismember_plaintext(excludeKey, fqdn)) {
            return false;
        }

        final String key = REDIS_FLEX_LISTS + "~" + ip + REDIS_LIST_SUFFIX;
        String check = fqdn;
        while (true) {
            final boolean found = redis.sismember_plaintext(key, check);
            if (found) return true;
            final int dotPos = check.indexOf('.');
            if (dotPos == check.length()) return false;
            check = check.substring(dotPos+1);
            if (!check.contains(".")) return false;
        }
    }

    AppRuleDriver getNext();
    void setNext(AppRuleDriver next);
    default boolean hasNext() { return getNext() != null; }

    default void init(JsonNode config,
                      JsonNode userConfig,
                      BubbleApp app,
                      AppRule rule,
                      AppMatcher matcher,
                      Account account,
                      Device device) {}

    default void initQuick(JsonNode config,
                           JsonNode userConfig,
                           BubbleApp app,
                           AppRule rule,
                           AppMatcher matcher,
                           Account account,
                           Device device) {
        init(config, userConfig, app, rule, matcher, account, device);
    }

    default FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                           FilterMatchersRequest filter,
                                           Account account,
                                           Device device,
                                           Request req,
                                           ContainerRequest request) {
        return FilterMatchDecision.match;
    }

    default InputStream filterRequest(InputStream in) {
        if (hasNext()) return doFilterRequest(getNext().filterRequest(in));
        return doFilterRequest(in);
    }

    default InputStream doFilterRequest(InputStream in) { return in; }

    default InputStream filterResponse(FilterHttpRequest filterRequest, InputStream in, Charset charset) {
        if (hasNext()) return doFilterResponse(filterRequest, getNext().filterResponse(filterRequest, in, charset), charset);
        return doFilterResponse(filterRequest, in, charset);
    }

    default InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in, Charset charset) { return in; }

    default String resolveResource(String res, Map<String, Object> ctx) {
        final String resource = locateResource(res);
        if (resource == null || resource.trim().length() == 0) return "";

        final Handlebars handlebars = getHandlebars();
        if (handlebars == null) return resource;

        final String resolved = HandlebarsUtil.apply(handlebars, resource, ctx);
        return resolved == null ? "" : resolved;
    }

    default Handlebars getHandlebars() { return null; }

    default String locateResource(String res) {
        if (!res.startsWith("@")) return res;
        final String prefix = getPackagePath(getClass()) + "/" + getClass().getSimpleName();
        final String path = res.substring(1);
        switch (res) {
            case "@css": case "@js":
                try { return stream2string(prefix + "." + path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "." + path); } catch (Exception e) {}
                break;
            case "@body":
                try { return stream2string(prefix + "_body.html.hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "_body.html"); } catch (Exception e) {}
                break;
            default:
                try { return stream2string(prefix + "_" + path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(prefix + "_" + path ); } catch (Exception e) {}
                try { return stream2string(path + ".hbs"); } catch (Exception e) {}
                try { return stream2string(path); } catch (Exception e) {}
        }
        return null;
    }

    default byte[] locateBinaryResource(String res) {
        if (res.startsWith("@")) res = res.substring(1);
        final String prefix = getPackagePath(getClass()) + "/" + getClass().getSimpleName();
        try { return stream2bytes(prefix + "_" + res ); } catch (Exception e) {}
        try { return stream2bytes(res); } catch (Exception e) {}
        return null;
    }

    default ConnectionCheckResponse checkConnection(AppRuleHarness harness,
                                                    Account account,
                                                    Device device,
                                                    String clientAddr,
                                                    String serverAddr,
                                                    String fqdn) {
        return ConnectionCheckResponse.noop;
    }

    default boolean couldModify(FilterHttpRequest request) { return false; }

    default JsonNode upgradeRuleConfig(JsonNode sageRuleConfig,
                                       JsonNode localRuleConfig) {
        return sageRuleConfig;
    }

    default Object readData(String id) { return null; }

}
