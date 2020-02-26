/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.auth;

import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.ApiConstants.ALWAYS_TRUE;

public interface RenderedMessage {

    Logger log = LoggerFactory.getLogger(RenderedMessage.class);

    Comparator<RenderedMessage> SORT_CTIME_DESC = Comparator.comparingLong(RenderedMessage::getCtime).reversed();
    Comparator<RenderedMessage> SORT_CTIME_ASC = Comparator.comparingLong(RenderedMessage::getCtime);

    Map<String, Object> getCtx();
    long getCtime();

    default <T> T del(String key) { getCtx().remove(key); return (T) this; }

    default boolean hasContext(String key, String val) {
        if (getCtx() == null) return false;
        try {
            final Object v = ReflectionUtil.get(getCtime(), key);
            return v != null && v.toString().equals(val);
        } catch (Exception e) {
            log.warn("hasContext: "+e);
            return false;
        }
    }

    @JsonIgnore default AccountMessage getAccountMessage() { return (AccountMessage) getCtx().get("message"); }
    @JsonIgnore default AccountMessageType getMessageType() { return getAccountMessage().getMessageType(); }
    @JsonIgnore default AccountAction getAction() { return getAccountMessage().getAction(); }
    @JsonIgnore default ActionTarget getTarget() { return getAccountMessage().getTarget(); }

    static List<RenderedMessage> inbox(Map<String, ArrayList<RenderedMessage>> spool, String to) {
        return filteredInbox(spool, to, null, null);
    }

    static List<RenderedMessage> filteredInbox(Map<String, ArrayList<RenderedMessage>> spool,
                                               String to,
                                               Predicate<RenderedMessage> test) {
        return filteredInbox(spool, to, test, null);
    }

    static List<RenderedMessage> filteredInbox(Map<String, ArrayList<RenderedMessage>> spool,
                                               String to,
                                               Predicate<RenderedMessage> test,
                                               Comparator<RenderedMessage> sort) {
        if (test == null) test = ALWAYS_TRUE;
        if (sort == null) sort = SORT_CTIME_DESC;
        final Comparator<RenderedMessage> comp = sort;

        final ArrayList<RenderedMessage> inbox = spool.computeIfAbsent(to, m -> new ArrayList<>());
        final ArrayList<RenderedMessage> found = new ArrayList<>(inbox.stream()
                .filter(test)
                .collect(Collectors.toCollection(() -> new TreeSet<>(comp))));
        found.forEach(m -> m.del("configuration"));  // don't send this over the wire. it's huge.
        return found;
    }

}
