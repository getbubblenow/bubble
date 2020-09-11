/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.message;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.collection.ArrayUtil;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.MESSAGE_RESOURCE_BASE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.*;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@Service @Slf4j
public class MessageService {

    public static final String MESSAGE_RESOURCE_PATH = "/server/";
    public static final String PAGE_TEMPLATES_PATH = "pages/";
    public static final String PAGE_TEMPLATES_SUFFIX = ".html.hbs";
    public static final String RESOURCE_MESSAGES_PROPS = "ResourceMessages.properties";

    public static final String[] PRE_AUTH_MESSAGE_GROUPS = {"pre_auth", "countries", "timezones"};
    public static final String APPS_MESSAGE_GROUP = "apps";
    public static final String[] STANDARD_MESSAGE_GROUPS
            = ArrayUtil.append(PRE_AUTH_MESSAGE_GROUPS, "post_auth");
    public static final String[] ALL_MESSAGE_GROUPS
            = ArrayUtil.append(STANDARD_MESSAGE_GROUPS, APPS_MESSAGE_GROUP);

    private final Map<String, Properties> messageCache = new ConcurrentHashMap<>();

    public static String normalizeLocale(String locale) {
        if (empty(locale)) return locale;
        final int uPos = locale.indexOf('_');
        if (uPos == -1) return locale;
        if (uPos == locale.length()-1) return locale.substring(locale.length()-1);
        return locale.substring(0, uPos).toLowerCase()+'_'+locale.substring(uPos+1).toUpperCase();
    }

    public Properties loadMessages(String locale, String group) {
        if (!ArrayUtils.contains(ALL_MESSAGE_GROUPS, group)) return die("loadMessages: invalid group: "+group);
        final String cacheKey = locale + "_" + group;
        return messageCache.computeIfAbsent(cacheKey, k -> {
            final Properties props = new Properties();
            try {
                props.load(new BufferedReader(new InputStreamReader(loadResourceAsStream(MESSAGE_RESOURCE_BASE + locale + MESSAGE_RESOURCE_PATH + group + "/" + RESOURCE_MESSAGES_PROPS), UTF8cs)));
            } catch (IOException e) {
                return die("loadMessages("+locale+", "+group+"): "+shortError(e), e);
            }
            return props;
        });
    }

    public Map<String, String> formatMessages(Properties props) {
        return formatMessages(props, MessageResourceFormat.underscore);
    }

    public Map<String, String> formatMessages(Properties props, MessageResourceFormat format) {
        final Map<String, String> messages = new LinkedHashMap<>();
        props.forEach((key, value) -> messages.put(format.format(key.toString()), value.toString()));
        return messages;
    }

    private final Map<String, Map<String, String>> standardMessagesCache = new ConcurrentHashMap<>();

    public Map<String, String> formatStandardMessages(String locale) {
        return standardMessagesCache.computeIfAbsent(locale, k -> {
            final Map<String, String> m = new LinkedHashMap<>();
            for (String group : STANDARD_MESSAGE_GROUPS) {
                m.putAll(formatMessages(loadMessages(locale, group)));
            }
            return m;
        });
    }

    private final Map<String, String> pageTemplateCache = new ConcurrentHashMap<>(10);

    public String loadPageTemplate(String locale, String templatePath) {
        final String key = locale + ":" + templatePath;
        return pageTemplateCache.computeIfAbsent(key, k -> {
            final String path = MESSAGE_RESOURCE_BASE + locale + MESSAGE_RESOURCE_PATH + PAGE_TEMPLATES_PATH + templatePath + PAGE_TEMPLATES_SUFFIX;
            return loadResourceAsStringOrDie(path);
        });
    }
}
