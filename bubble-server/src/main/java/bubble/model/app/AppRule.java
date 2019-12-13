package bubble.model.app;

import bubble.model.account.Account;
import bubble.rule.AppRuleDriver;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_RULES;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_RULES, listFields={"name", "app", "driver", "configJson"})
@ECTypeFields(list={"name", "app", "driver", "configJson"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECTypeChildren(uriPrefix=EP_RULES+"/{AppRule.name}", value={
        @ECTypeChild(type= AppData.class, backref="rule"),
})
@ECIndexes({
        @ECIndex(unique=true, of={"account", "app", "name"}),
        @ECIndex(of={"account", "name"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppRule extends IdentifiableBaseParentEntity implements AppTemplateEntity, HasPriority {

    public static final String[] VALUE_FIELDS = {"driver", "configJson", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS, "name");

    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    public boolean isSameAccount(String accountUuid) {
        return (account == null && accountUuid == null) || (account != null && account.equals(accountUuid));
    }

    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template () { return template == null || template; }

    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @Column(nullable=false)
    @Getter @Setter private Integer priority = 0;

    @ECForeignKey(entity=RuleDriver.class)
    @HasValue(message="err.driver.required")
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String driver;

    public AppRuleDriver initDriver(RuleDriver driver, AppMatcher matcher) {
        final AppRuleDriver d = driver.getDriver();
        d.init(json(configJson, JsonNode.class), driver.getUserConfig(), this, matcher);
        return d;
    }

    @Size(max=500000, message="err.configJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(500000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String configJson;

    public AppRule(AppRule other) {
        copy(this, other, CREATE_FIELDS);
        setUuid(null);
    }

    public AppRule(BubbleApp app, AppRule rule) {
        copy(this, rule, CREATE_FIELDS);
        setApp(app.getUuid());
        setUuid(null);
    }

    @Override public Identifiable update(Identifiable other) { copy(this, other, VALUE_FIELDS); return this; }

    @Transient public JsonNode getConfig () { return json(configJson, JsonNode.class); }
    public AppRule setConfig(JsonNode config) { return setConfigJson(json(config)); }

}
