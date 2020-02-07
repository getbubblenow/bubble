package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import bubble.model.app.config.AppDataConfig;
import bubble.model.app.config.AppDataField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=APPS_ENDPOINT, listFields={"name", "url", "description", "account", "template", "enabled"})
@ECTypeChildren(uriPrefix=EP_APPS+"/{BubbleApp.name}", value={
        @ECTypeChild(type=AppSite.class, backref="app"),
        @ECTypeChild(type=AppMatcher.class, backref="app"),
        @ECTypeChild(type=AppRule.class, backref="app"),
        @ECTypeChild(type=AppMessage.class, backref="app"),
        @ECTypeChild(type=AppData.class, backref="app")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"account", "templateApp"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class BubbleApp extends IdentifiableBaseParentEntity implements AccountTemplate {

    private static final String[] VALUE_FIELDS = {"url", "description", "template", "enabled", "dataConfig"};

    public BubbleApp(Account account, BubbleApp app) {
        copy(this, app);
        setAccount(account.getUuid());
        setUuid(null);
    }

    public BubbleApp(BubbleApp other) { copy(this, other); setUuid(null); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, VALUE_FIELDS); return this; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String account;

    @ECSearchable(filter=true) @ECField(index=30)
    @HasValue(message="err.url.required")
    @Size(max=1024, message="err.url.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(1024+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String url;

    @ECSearchable(filter=true) @ECField(index=40)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @Column(length=100000, nullable=false) @ECField(index=50)
    @JsonIgnore @Getter @Setter private String dataConfigJson;

    @Transient public AppDataConfig getDataConfig () { return dataConfigJson == null ? null : ensureDefaults(json(dataConfigJson, AppDataConfig.class)); }
    public BubbleApp setDataConfig (AppDataConfig adc) { return setDataConfigJson(adc == null ? null : json(adc, DB_JSON_MAPPER)); }
    public boolean hasDataConfig () { return getDataConfig() != null; }

    private AppDataConfig ensureDefaults(AppDataConfig adc) {
        for (AppDataField field : adc.getFields()) {
            if (!adc.hasConfigField(field)) {
                adc.setConfigFields(ArrayUtil.append(adc.getConfigFields(), field));
            }
        }
        return adc;
    }

    // We do NOT add @ECForeignKey here, since the template BubbleApp will not be copied
    // to a new node. This App will become a template/root BubbleApp for a new node, if it
    // is owned by a user and applicable to the BubblePlan (via BubblePlanApp)
    // For system apps, this can be null
    @ECField(index=60)
    @Column(length=UUID_MAXLEN, updatable=false)
    @Getter @Setter private String templateApp;

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=80)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @ECSearchable @ECField(index=90)
    @ECIndex @Getter @Setter private Boolean needsUpdate = false;

    @JsonIgnore @Transient @Getter @Setter private transient boolean flushRuleCache = false;

}
