/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.NameAndValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import java.io.Serializable;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Embeddable @NoArgsConstructor @Accessors(chain=true)
public class BubbleTags implements Serializable {

    @Size(max=100000, message="err.tagsJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String tagsJson;

    @Transient public NameAndValue[] getTags() { return tagsJson == null ? null : json(tagsJson, NameAndValue[].class); }
    public BubbleTags setTags(NameAndValue[] tags) { return setTagsJson(tags == null ? null : json(tags, DB_JSON_MAPPER)); }

    public String getTag (String name) {
        final NameAndValue[] tags = getTags();
        return tags == null ? null : NameAndValue.find(tags, name);
    }
    public boolean hasTag (String name) { return getTag(name) != null; }

    public BubbleTags setTag (String name, String value) {
        NameAndValue[] tags = getTags();
        if (tags == null) tags = NameAndValue.EMPTY_ARRAY;

        boolean found = false;
        for (NameAndValue nv : tags) {
            if (nv.getName().equals(name)) {
                nv.setValue(value);
                found = true;
                break;
            }
        }
        if (!found) {
            return setTags(ArrayUtil.append(tags, new NameAndValue(name, value)));
        } else {
            return setTags(tags);
        }
    }

}
