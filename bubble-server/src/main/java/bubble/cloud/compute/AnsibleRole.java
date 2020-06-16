/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;

import javax.persistence.Column;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.Arrays;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;

@NoArgsConstructor @Accessors(chain=true)
public class AnsibleRole {

    @Getter @Setter private String name;

    @ECSearchable(filter=true) @ECField(index=30)
    @Size(max=10000, message="err.description.length")
    @Getter @Setter private String description;

    @Column(updatable=false, length=10000)
    @JsonIgnore @Getter @Setter private String configJson;
    public boolean hasConfig () { return configJson != null; }

    @Transient public NameAndValue[] getConfig () { return configJson == null ? null : json(configJson, NameAndValue[].class); }
    public AnsibleRole setConfig(NameAndValue[] config) { return setConfigJson(config == null ? null : json(config, DB_JSON_MAPPER)); }

    @Column(updatable=false, length=1000) @ECField(index=80)
    @JsonIgnore @Getter @Setter private String optionalConfigNamesJson;
    public boolean hasOptionalConfigNames () { return optionalConfigNamesJson != null; }

    @Transient public String[] getOptionalConfigNames() { return optionalConfigNamesJson == null ? null : json(optionalConfigNamesJson, String[].class); }
    public AnsibleRole setOptionalConfigNames(String[] names) { return setOptionalConfigNamesJson(name == null ? null : json(names, DB_JSON_MAPPER)); }

    public boolean isOptionalConfigName(String cfgName) {
        final String[] names = getOptionalConfigNames();
        if (names == null) return false;
        return Arrays.asList(names).contains(cfgName);
    }
}
