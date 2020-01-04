package bubble.model.app;

import bubble.model.account.Account;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.regex.Pattern;

import static bubble.ApiConstants.EP_MATCHERS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_MATCHERS, listFields={"name", "app", "fqdn", "urlRegex", "rule"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "app", "name"}),
        @ECIndex(of={"account", "name"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppMatcher extends IdentifiableBase implements AppTemplateEntity {

    public static final String[] VALUE_FIELDS = {"fqdn", "urlRegex", "rule", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS, "name", "site");

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable
    @ECForeignKey(entity=AppSite.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String site;

    @ECSearchable(filter=true)
    @HasValue(message="err.fqdn.required")
    @Size(max=1024, message="err.fqdn.length")
    @ECIndex @Column(nullable=false, length=1024)
    @Getter @Setter private String fqdn;

    @ECSearchable(filter=true)
    @HasValue(message="err.urlRegex.required")
    @Size(max=1024, message="err.urlRegex.length")
    @Type(type=ENCRYPTED_STRING) @Column(nullable=false, columnDefinition="varchar("+(1024+ENC_PAD)+") UNIQUE")
    @Getter @Setter private String urlRegex;

    @Transient @JsonIgnore public Pattern getPattern () { return Pattern.compile(getUrlRegex()); }

    public boolean matches (String value) { return getPattern().matcher(value).find(); }

    @ECSearchable
    @ECForeignKey(entity=AppRule.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String rule;

    @ECSearchable
    @Column(nullable=false)
    @Getter @Setter private Boolean blocked = false;
    public boolean blocked() { return blocked != null && blocked; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template () { return template == null || template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    public AppMatcher(AppMatcher other) {
        copy(this, other, CREATE_FIELDS);
        setUuid(null);
    }

    @Override public Identifiable update(Identifiable other) { copy(this, other, VALUE_FIELDS); return this; }

}
