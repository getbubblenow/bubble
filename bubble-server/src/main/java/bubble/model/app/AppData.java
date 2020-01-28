package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.rule.RuleConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.model.search.SearchBoundComparison;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true, pluralDisplayName="App Data")
@ECTypeURIs(baseURI= EP_DATA, listFields={"app", "key", "data", "expiration"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"app", "matcher", "device", "key"}),
        @ECIndex(unique=true, of={"app", "site", "device", "key"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "app", "device"}),
        @ECIndex(of={"account", "app", "device", "key"}),
        @ECIndex(of={"account", "app", "site"}),
        @ECIndex(of={"account", "app", "site", "device"}),
        @ECIndex(of={"account", "app", "site", "device", "key"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppData extends IdentifiableBase implements AppTemplateEntity {

    public static final String[] VALUE_FIELDS = {"data", "expiration", "template", "enabled"};

    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS,
            "account", "device", "app", "site", "matcher", "key");

    public AppData(RuleConfig config) {
        setMatcher(config.getMatcher());
        setApp(config.getApp());
    }

    public AppData(AppData other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) {
        copy(this, other, VALUE_FIELDS);
        return this;
    }

    public boolean readable(Account caller) {
        if (caller == null) return false;
        return caller.admin() || caller.getUuid().equals(getAccount());
    }

    @Override @Transient public String getName() { return getKey(); }
    public AppData setName(String n) { return setKey(n); }

    @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(fkDepth=ECForeignKeySearchDepth.shallow) @ECField(index=20)
    @ECForeignKey(entity=Device.class)
    @Column(updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String device;

    @ECSearchable(fkDepth=ECForeignKeySearchDepth.none) @ECField(index=30)
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;
    public boolean hasApp () { return app != null; }

    @ECSearchable(fkDepth=ECForeignKeySearchDepth.none) @ECField(index=40)
    @ECForeignKey(entity=AppMatcher.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String matcher;
    public boolean hasMatcher() { return matcher != null; }

    @ECSearchable(fkDepth=ECForeignKeySearchDepth.none) @ECField(index=50)
    @ECForeignKey(entity=AppSite.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String site;
    public boolean hasSite() { return site != null; }

    @ECSearchable(filter=true, operators={
            SearchBoundComparison.eq,
            SearchBoundComparison.ne,
            SearchBoundComparison.like,
            SearchBoundComparison.like_any
    })
    @ECField(index=60)
    @HasValue(message="err.key.required")
    @ECIndex @Column(nullable=false, updatable=false, length=5000)
    @Getter @Setter private String key;
    public boolean hasKey () { return key != null; }

    @ECSearchable(filter=true) @ECField(index=70)
    @Size(max=100000, message="err.data.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(100000+ENC_PAD)+")")
    @Getter @Setter private String data;

    public AppData incrData () {
        long val;
        try {
            val = Long.parseLong(data);
        } catch (Exception e) {
            val = 0;
        }
        return setData(String.valueOf(val+1));
    }

    @ECSearchable(type=EntityFieldType.expiration_time) @ECField(index=80)
    @ECIndex @Getter @Setter private Long expiration;
    public boolean expired () { return expiration != null && now() > expiration; }

    @ECSearchable @ECField(index=100)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=110)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @JsonProperty @Override public long getCtime () { return super.getCtime(); }
    @JsonProperty @Override public long getMtime () { return super.getMtime(); }

}
