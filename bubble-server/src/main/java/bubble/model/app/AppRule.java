/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.rule.AppRuleDriver;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static bubble.ApiConstants.EP_RULES;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_RULES, listFields={"name", "app", "driver", "configJson"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECTypeChildren(uriPrefix=EP_RULES+"/{AppRule.name}", value={
        @ECTypeChild(type=AppData.class, backref="rule"),
})
@ECIndexes({
        @ECIndex(unique=true, of={"account", "app", "name"}),
        @ECIndex(of={"account", "name"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppRule extends IdentifiableBaseParentEntity implements AppTemplateEntity, HasPriority {

    public static final String[] UPDATE_FIELDS = {"configJson", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "driver", "app", "name");

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    public boolean isSameAccount(String accountUuid) {
        return (account == null && accountUuid == null) || (account != null && account.equals(accountUuid));
    }

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable @ECField(index=40)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @ECSearchable @ECField(index=60)
    @Column(nullable=false)
    @Getter @Setter private Integer priority = 0;

    @ECSearchable @ECField(index=70)
    @ECForeignKey(entity=RuleDriver.class)
    @HasValue(message="err.driver.required")
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String driver;

    public AppRuleDriver initDriver(BubbleConfiguration configuration,
                                    BubbleApp app,
                                    RuleDriver driver,
                                    AppMatcher matcher,
                                    Account account,
                                    Device device) {
        final AppRuleDriver d = configuration.autowire(driver.getDriver());
        d.init(json(configJson, JsonNode.class), driver.getUserConfig(), app, this, matcher, account, device);
        return d;
    }

    public AppRuleDriver initQuickDriver(BubbleApp app,
                                         RuleDriver driver,
                                         AppMatcher matcher,
                                         Account account,
                                         Device device) {
        final AppRuleDriver d = driver.getDriver();
        d.initQuick(json(configJson, JsonNode.class), driver.getUserConfig(), app, this, matcher, account, device);
        return d;
    }

    @ECSearchable(filter=true) @ECField(index=80, type=EntityFieldType.json)
    @Size(max=500000, message="err.configJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(500000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String configJson;

    @JsonIgnore @Transient @Getter @Setter private String previousConfigJson;
    public boolean configJsonChanged () {
        return (previousConfigJson == null && configJson != null) || (previousConfigJson != null && !previousConfigJson.equals(configJson));
    }

    public AppRule(AppRule other) {
        copy(this, other, CREATE_FIELDS);
        setUuid(null);
    }

    public AppRule(BubbleApp app, AppRule rule) {
        copy(this, rule, CREATE_FIELDS);
        setApp(app.getUuid());
        setUuid(null);
    }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public <T extends AppTemplateEntity> void upgrade(T sageRule, BubbleConfiguration configuration) {
        final RuleDriverDAO ruleDriverDAO = (RuleDriverDAO) configuration.getDaoForEntityClass(RuleDriver.class);
        final RuleDriver ruleDriver = ruleDriverDAO.findByUuid(getDriver());
        final AppRuleDriver appRuleDriver = ruleDriver.getDriver();
        final JsonNode upgradedConfig = appRuleDriver.upgradeRuleConfig(getConfig(), ((AppRule) sageRule).getConfig());
        setConfig(upgradedConfig);
    }

    @Transient public JsonNode getConfig () { return json(configJson, JsonNode.class); }
    public AppRule setConfig(JsonNode config) { return setConfigJson(json(config, DB_JSON_MAPPER)); }

}
