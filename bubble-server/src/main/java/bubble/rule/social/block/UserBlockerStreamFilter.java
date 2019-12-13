package bubble.rule.social.block;

import bubble.ApiConstants;
import bubble.BubbleHandlebars;
import bubble.model.app.AppData;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.ning.http.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.regex.RegexChunk;
import org.cobbzilla.util.io.regex.RegexChunkStreamer;
import org.cobbzilla.util.io.regex.RegexFilterResult;
import org.cobbzilla.util.io.regex.RegexStreamFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static bubble.rule.social.block.UserBlockerConfig.STANDARD_JS_ENGINE;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class UserBlockerStreamFilter implements RegexStreamFilter {

    public static final String PROP_USER_ID = "userId";
    public static final String PROP_BLOCKED_USER = "blockedUser";
    public static final String PROP_BLOCK_URL = "blockUrl";
    public static final String PROP_UNBLOCK_URL = "unblockUrl";
    public static final String PROP_CHUNK_START_REGEX = "chunkStartRegex";

    private AppMatcher matcher;
    private AppRule rule;

    public UserBlockerStreamFilter(AppMatcher matcher, AppRule rule) {
        this.matcher = matcher;
        this.rule = rule;
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

        while (pager.hasMoreChunks()) {
            final RegexChunk pageChunk = pager.nextChunk();
            if (pageChunk.isPartial() || (!pager.hasMoreChunks() && !eof)) {
                // push the last chunk back onto the unprocessed char array, unless we are at eof
                return new RegexFilterResult(result, pageChunk.getData().length());
            }
            switch (pageChunk.getType()) {
                case content:
                    append(result, pageChunk.getData());
                    break;

                case chunk:
                    switch (state) {
                        case seeking_comments:
                            final String userId = pageChunk.getProperty(PROP_USER_ID);
                            if (userId == null) {
                                log.warn("apply: no userId found for chunk: "+pageChunk);
                                append(result, pageChunk.getData());
                            } else if (!isUserBlocked(userId)) {
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

        return new RegexFilterResult(result, 0);
    }

    private void append(StringBuilder result, String data) { result.append(data.replace("\0", "")); }

    protected String decorate(RegexChunk data) {
        if (!config.hasCommentDecorator()) {
            return data.getData();
        }
        final String userId = data.getProperty(PROP_USER_ID);
        final AppData appData = new AppData()
                .setAccount(rule.getAccount())
                .setApp(rule.getApp())
                .setMatcher(matcher.getUuid())
                .setSite(matcher.getSite())
                .setKey(userId)
                .setData(Boolean.TRUE.toString());
        final String blockUrl = ApiConstants.DATA_ENDPOINT + "/"+ Base64.encode(json(appData, COMPACT_MAPPER).getBytes());
        final Map<String, Object> ctx = new HashMap<>(data.getProperties());
        ctx.put(PROP_BLOCK_URL, blockUrl);
        return config.getCommentDecorator().decorate(BubbleHandlebars.instance.getHandlebars(), data.getData(), ctx);
    }

    protected String getReplacement(String userId, RegexChunk pageChunk) {
        if (!config.hasBlockedCommentReplacement()) return "";
        final Map<String, Object> ctx = new HashMap<>();
        final AppData appData = new AppData()
                .setAccount(rule.getAccount())
                .setApp(rule.getApp())
                .setMatcher(matcher.getUuid())
                .setSite(matcher.getSite())
                .setKey(userId)
                .setData(Boolean.FALSE.toString());
        final String unblockUrl = ApiConstants.DATA_ENDPOINT+"/"+Base64.encode(json(appData, COMPACT_MAPPER).getBytes());
        ctx.put(PROP_BLOCKED_USER, userId);
        ctx.put(PROP_UNBLOCK_URL, unblockUrl);

        final Matcher m = config.getChunkStartPattern().matcher(pageChunk.getData());
        if (m.find()) {
            ctx.put(PROP_CHUNK_START_REGEX, pageChunk.getData().substring(m.start(), m.end()));
        }
        return HandlebarsUtil.apply(config.getHandlebars(), config.getBlockedCommentReplacement(), ctx);
    }

    protected boolean isUserBlocked(String userId) {
        // todo: cache these lookups for a while
        if (userId == null) return false;
        final String data = config.getDataDAO().findValueByAppAndSiteAndKey(config.getApp(), matcher.getSite(), userId);
        return Boolean.parseBoolean(data);
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
