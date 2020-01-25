package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.APPS_ENDPOINT;
import static bubble.ApiConstants.EP_APPS;
import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=APPS_ENDPOINT, listFields={"name", "url", "description", "account", "template", "enabled"})
@ECTypeChildren(uriPrefix=EP_APPS+"/{BubbleApp.name}", value={
        @ECTypeChild(type=AppSite.class, backref="app"),
        @ECTypeChild(type=AppMatcher.class, backref="app"),
        @ECTypeChild(type=AppRule.class, backref="app"),
        @ECTypeChild(type=AppData.class, backref="app")
})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class BubbleApp extends IdentifiableBaseParentEntity implements AccountTemplate {

    private static final String[] VALUE_FIELDS = {"url", "description", "template", "enabled"};

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

    @Column(nullable=false, length=30)
    @Enumerated(EnumType.STRING) @ECField(index=50)
    @Getter @Setter private BubbleAppDataPresentation dataPresentation;

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return bool(template); }

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable @ECField(index=80)
    @ECIndex @Getter @Setter private Boolean needsUpdate = false;

    public BubbleApp(Account account, BubbleApp app) {
        copy(this, app);
        setAccount(account.getUuid());
        setUuid(null);
    }

    public BubbleApp(BubbleApp other) { copy(this, other); setUuid(null); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, VALUE_FIELDS); return this; }

}
