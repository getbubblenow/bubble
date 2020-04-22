/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model;

import bubble.model.account.HasAccount;
import org.cobbzilla.wizard.model.Identifiable;

import java.util.Collection;
import java.util.Collections;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public interface HasBubbleTags<T extends Identifiable> extends HasAccount {

    BubbleTags getTags();
    T setTags(BubbleTags tags);

    default Collection<String> validTags() { return Collections.emptyList(); }

    default boolean hasTags () { return getTags() != null; }

    default boolean hasTag (String t) { return hasTags() && getTags().hasTag(t); }

    default String getTag (String t, String orElse) { return hasTag(t) ? getTags().getTag(t) : orElse; }

    default boolean getBooleanTag (String t, boolean orElse) { return hasTag(t) ? Boolean.valueOf(getTags().getTag(t)) : orElse; }

    default String getTag (String t) { return getTag(t, null); }

    default T setTag(String t, boolean val) { return setTag(t, ""+val); }

    default T setTag(String t, String val) {
        if (!validTags().contains(t)) throw invalidEx("err.tag.invalid", "Not a valid tag: "+t+": "+val, t);
        if (getTags() == null) setTags(new BubbleTags());
        getTags().setTag(t, val);
        return (T) this;
    }
}
