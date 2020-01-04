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
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.*;
import java.util.Locale;

import static bubble.ApiConstants.*;
import static bubble.ApiConstants.DEFAULT_LOCALE;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@ECType(root=true) @ECTypeURIs(baseURI=DRIVERS_ENDPOINT, listFields={"name", "author", "url"})
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

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @HasValue(message="err.name.required")
    @ECIndex @Column(length=200, nullable=false, updatable=false)
    @Getter @Setter private String name;

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return template != null && template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable(filter=true)
    @Column(length=200)
    @Getter @Setter private String author;

    @ECSearchable(filter=true)
    @Column(length=1024)
    @Getter @Setter private String url;

    @ECSearchable
    @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String driverClass;

    @Column(length=100000)
    @JsonIgnore @Getter @Setter private String userConfigJson;

    @Transient public RuleDriver setUserConfig (JsonNode json) { return setUserConfigJson(json(json)); }
    public JsonNode getUserConfig () { return json(userConfigJson, JsonNode.class); }

    @Transient @JsonIgnore @Getter(lazy=true) private final AppRuleDriver driver = instantiate(this.driverClass);

    @Embedded @Getter @Setter private SemanticVersion version;

    @ECSearchable
    @ECIndex @Getter @Setter private Boolean needsUpdate = false;

    @Transient @Getter @Setter private AppRuleDriverDescriptor descriptor;

    public AppRuleDriverDescriptor getDescriptor(Locale locale) {
        final String localeString = locale != null ? locale.toString() : DEFAULT_LOCALE;
        final AppRuleDriverDescriptor localized;
        final String prefix = driverClass.replace(".", "/");
        try {
            localized = json(stream2string(prefix + "_descriptor_" + localeString + ".json"), AppRuleDriverDescriptor.class);
        } catch (Exception e) {
            // no localized version, just the regular
            return json(stream2string(prefix + "_descriptor.json"), AppRuleDriverDescriptor.class);
        }

        // we have a localized version, is there a general version?
        final AppRuleDriverDescriptor descriptor;
        try {
            descriptor = json(stream2string(prefix + "_descriptor.json"), AppRuleDriverDescriptor.class);
        } catch (Exception e) {
            // only localized version is available
            return localized;
        }
        try {
            // start with general version, since it has all general settings. but then overwrite
            // any localized labels
            if (localized.hasLabels()) {
                for (NameAndValue label : localized.getLabels()) {

                    descriptor.setLabel(label);
                }
            }
            return descriptor;
        } catch (Exception e) {
            // error merging, just return general version
            return descriptor;
        }
    }

    public RuleDriver initDescriptor(Locale locale) { setDescriptor(getDescriptor(locale)); return this; }

}
