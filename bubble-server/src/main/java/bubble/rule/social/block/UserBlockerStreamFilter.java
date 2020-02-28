/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.social.block;

import bubble.BubbleHandlebars;
import bubble.dao.app.AppDataDAO;
import bubble.model.app.AppData;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.regex.RegexChunk;
import org.cobbzilla.util.io.regex.RegexChunkStreamer;
import org.cobbzilla.util.io.regex.RegexFilterResult;
import org.cobbzilla.util.io.regex.RegexStreamFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static bubble.ApiConstants.*;
import static bubble.rule.AbstractAppRuleDriver.getDataId;
import static bubble.rule.social.block.UserBlockerConfig.STANDARD_JS_ENGINE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.urlEncode;

@Slf4j
public class UserBlockerStreamFilter implements RegexStreamFilter {

    public static final String PROP_USER_ID = "userId";
    public static final String PROP_BLOCKED_USER = "blockedUser";
    public static final String PROP_BLOCK_URL = "blockUrl";
    public static final String PROP_UNBLOCK_URL = "unblockUrl";
    public static final String PROP_CHUNK_START_REGEX = "chunkStartRegex";

    private String requestId;
    private AppMatcher matcher;
    private AppRule rule;
    private String apiBase;
    @Setter private AppDataDAO dataDAO;

    public UserBlockerStreamFilter(String requestId, AppMatcher matcher, AppRule rule, String apiBase) {
        this.requestId = requestId;
        this.matcher = matcher;
        this.rule = rule;
        this.apiBase = apiBase;
    }

    private enum UserBlockerStreamState { seeking_comments, blocking_comments }

    private UserBlockerConfig config;
    private UserBlockerStreamState state = UserBlockerStreamState.seeking_comments;
    private RegexChunk blockedComment = null;

    @Override public void configure(JsonNode config) { this.config = json(config, UserBlockerConfig.class); }

    @Override public RegexFilterResult apply(StringBuilder buffer, boolean eof) {

        final StringBuilder result = new StringBuilder(buffer.length());

        // turn buffer into a stream of PageChunks
        final RegexChunkStreamer pager = new RegexChunkStreamer(buffer, config, eof);

        int matchCount = 0;
        while (pager.hasMoreChunks()) {
            final RegexChunk pageChunk = pager.nextChunk();
            if (pageChunk.isPartial() || (!pager.hasMoreChunks() && !eof)) {
                // push the last chunk back onto the unprocessed char array, unless we are at eof
                return new RegexFilterResult(result, pageChunk.getData().length(), 0);
            }
            switch (pageChunk.getType()) {
                case content:
                    append(result, pageChunk.getData());
                    break;

                case chunk:
                    matchCount++;
                    switch (state) {
                        case seeking_comments:
                            final String userId = pageChunk.getProperty(PROP_USER_ID);
                            if (userId == null) {
                                log.warn("apply: no userId found for chunk: "+pageChunk);
                                append(result, pageChunk.getData());
                            } else if (!isUserBlocked(requestId, userId)) {
                                // add controls to unblocked comment, to allow it to be blocked
                                append(result, decorate(pageChunk));
                            } else {
                                blockedComment = pageChunk;
                                append(result, getReplacement(userId, pageChunk));
                                state = UserBlockerStreamState.blocking_comments;
                            }
                            break;

                        case blocking_comments:
                            if (!isCommentBlocked(blockedComment, pageChunk)) {
                                append(result, pageChunk.getData());
                                state = UserBlockerStreamState.seeking_comments;
                                blockedComment = null;
                            }
                            break;
                    }
                    break;
            }
        }

        return new RegexFilterResult(result, 0, matchCount);
    }

    private void append(StringBuilder result, String data) { result.append(data.replace("\0", "")); }

    protected String decorate(RegexChunk data) {
        if (!config.hasCommentDecorator()) {
            return data.getData();
        }
        final String userId = data.getProperty(PROP_USER_ID);
        final AppData appData = new AppData()
                .setKey(userId)
                .setData(Boolean.TRUE.toString());
        final String dataId = getDataId(requestId, matcher);
        final String blockUrl = BUBBLE_FILTER_PASSTHRU + apiBase + FILTER_HTTP_ENDPOINT + EP_DATA + "/" + dataId + EP_WRITE
                + "?" +Q_DATA + "=" +urlEncode(json(appData, COMPACT_MAPPER));
        final Map<String, Object> ctx = new HashMap<>(data.getProperties());
        ctx.put(PROP_BLOCK_URL, blockUrl);
        return config.getCommentDecorator().decorate(BubbleHandlebars.instance.getHandlebars(), data.getData(), ctx);
    }

    protected String getReplacement(String userId, RegexChunk pageChunk) {
        if (!config.hasBlockedCommentReplacement()) return "";
        final Map<String, Object> ctx = new HashMap<>();
        final AppData appData = new AppData()
                .setKey(userId)
                .setData(Boolean.FALSE.toString());
        final String dataId = getDataId(requestId, matcher);
        final String unblockUrl = BUBBLE_FILTER_PASSTHRU + apiBase + FILTER_HTTP_ENDPOINT + EP_DATA + "/" + dataId + EP_WRITE
                + "?" +Q_DATA + "=" +urlEncode(json(appData, COMPACT_MAPPER));
        ctx.put(PROP_BLOCKED_USER, userId);
        ctx.put(PROP_UNBLOCK_URL, unblockUrl);

        final Matcher m = config.getChunkStartPattern().matcher(pageChunk.getData());
        if (m.find()) {
            ctx.put(PROP_CHUNK_START_REGEX, pageChunk.getData().substring(m.start(), m.end()));
        }
        return HandlebarsUtil.apply(config.getHandlebars(), config.getBlockedCommentReplacement(), ctx);
    }

    private Map<String, Boolean> blockCache = new ExpirationMap<>(MINUTES.toMillis(1), ExpirationEvictionPolicy.atime);
    protected boolean isUserBlocked(String requestId, String userId) {
        return blockCache.computeIfAbsent(requestId+":"+userId, k -> {
            if (userId == null) return false;
            final String data = dataDAO.findValueByAppAndSiteAndKey(config.getApp(), matcher.getSite(), userId);
            return Boolean.parseBoolean(data);
        });
    }

    private boolean isCommentBlocked(RegexChunk blockedComment, RegexChunk candidateComment) {
        // if it's the same user, of course it is blocked
        final String blockedUser = blockedComment.getProperty(PROP_USER_ID);
        final String thisUser = candidateComment.getProperty(PROP_USER_ID);
        if (blockedUser != null && blockedUser.equals(thisUser)) return true;

        // evaluate the condition
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("blocked", blockedComment.getProperties());
        ctx.put("current", candidateComment.getProperties());
        try {
            return STANDARD_JS_ENGINE.evaluateBoolean(config.getBlockedCommentCheck(), ctx);
        } catch (Exception e) {
            log.error("isCommentBlocked: error evaluating "+config.getBlockedCommentCheck()+" with ctx="+ctx+": "+e);
            return false;
        }
    }
}
