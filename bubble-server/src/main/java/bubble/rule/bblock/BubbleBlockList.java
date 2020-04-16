/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.abp.BlockListSource;
import bubble.app.bblock.BlockListEntry;
import bubble.model.app.AppRule;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import javax.persistence.Transient;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.EMPTY_ARRAY;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class BubbleBlockList {

    public static final String[] UPDATE_FIELDS = {"name", "url", "description", "tags"};

    public BubbleBlockList(BlockListSource source) {
        setId(sha256_hex(source.getUrl()));
        setName(source.hasTitle() ? source.getTitle() : source.getUrl());
        setUrl(source.getUrl());
        setDescription(source.getDescription());
        setTags(EMPTY_ARRAY);
    }

    public BubbleBlockList update(BubbleBlockList other) {
        copy(this, other, UPDATE_FIELDS);
        return this;
    }

    @Getter @Setter private String id;
    public boolean hasId(String id) { return this.id != null && this.id.equals(id); }

    public String id() { return getId()+"/"+getName()+"/"+getUrl(); }

    @Getter @Setter private String name;
    @Getter @Setter private String description;
    @Getter @Setter private String[] tags;
    public boolean hasTags () { return !empty(getTags()); }

    public String getTagString() { return arrayToString(tags, ", ", "", false); }
    public BubbleBlockList setTagString (String val) {
        return setTags(splitAndTrim(val, ",").toArray(EMPTY_ARRAY));
    }

    @Getter @Setter private String url;
    public boolean hasUrl () { return !empty(url); }

    @Getter @Setter private String[] additionalEntries;
    public boolean hasAdditionalEntries () { return !empty(additionalEntries); }

    @Getter @Setter private Boolean enabled = true;
    public boolean enabled() { return enabled != null && enabled; }

    @JsonIgnore @Getter @Setter private AppRule rule;

    @Transient @Getter @Setter private Object response;  // non-standard config response (test URL) uses this

    public boolean hasEntry(String line) {
        return hasAdditionalEntries() && Arrays.asList(getAdditionalEntries()).contains(line);
    }

    public BubbleBlockList addEntry(String line) {
        if (!hasAdditionalEntries()) {
            setAdditionalEntries(new String[] {line});
        } else if (hasEntry(line)) {
            return this;
        } else {
            setAdditionalEntries(ArrayUtil.append(getAdditionalEntries(), line));
        }
        return this;
    }

    public BubbleBlockList removeRule(String id) {
        if (!hasAdditionalEntries()) return this;
        final List<String> retained = Arrays.stream(getAdditionalEntries()).filter(e -> !BlockListEntry.idFor(e).equals(id)).collect(Collectors.toList());
        if (retained.size() == getAdditionalEntries().length) {
            log.warn("removeRule: rule with id not found, nothing removed: "+id);
            return this;
        }
        return setAdditionalEntries(retained.toArray(EMPTY_ARRAY));
    }

}
