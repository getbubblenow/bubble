/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import bubble.rule.AppRuleDriver;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@ECType(root=true)
@ECTypeURIs(baseURI=DRIVERS_ENDPOINT, listFields={"name", "author", "url"})
@ECTypeChildren(uriPrefix=EP_DRIVERS+"/{RuleDriver.name}", value={
        @ECTypeChild(type=AppRule.class, backref="driver"),
        @ECTypeChild(type=AppData.class, backref="driver")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class RuleDriver extends IdentifiableBase implements AccountTemplate {

    public static final String[] UPDATE_FIELDS = { "author", "url", "template", "enabled", "version", "userConfig" };
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "driverClass");

    public RuleDriver(RuleDriver other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable driver) {
        copy(this, driver, UPDATE_FIELDS);
        return this;
    }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(length=200, nullable=false, updatable=false)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=40)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @ECSearchable(filter=true) @ECField(index=50, type=EntityFieldType.opaque_string)
    @Column(length=200)
    @Getter @Setter private String author;

    @ECSearchable(filter=true) @ECField(index=60)
    @Column(length=1024)
    @Getter @Setter private String url;

    @ECSearchable @ECField(index=70)
    @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String driverClass;

    @Column(length=100000) @ECField(index=80)
    @JsonIgnore @Getter @Setter private String userConfigJson;

    @Transient public RuleDriver setUserConfig (JsonNode json) { return setUserConfigJson(json(json, DB_JSON_MAPPER)); }
    public JsonNode getUserConfig () { return json(userConfigJson, JsonNode.class); }

    @Transient @JsonIgnore @Getter(lazy=true) private final AppRuleDriver driver = instantiate(this.driverClass);

    @Embedded @Getter @Setter private SemanticVersion version;

    @ECSearchable @ECField(index=90)
    @ECIndex @Getter @Setter private Boolean needsUpdate = false;

}
