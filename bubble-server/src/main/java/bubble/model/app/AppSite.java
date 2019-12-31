package bubble.model.app;

import bubble.model.account.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;

import static bubble.ApiConstants.EP_SITES;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_SITES, listFields={"name", "app", "description", "url"})
@ECTypeFields(list={"name", "app", "description", "url"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECTypeChildren(uriPrefix=EP_SITES+"/{AppSite.name}", value={
        @ECTypeChild(type=AppData.class, backref="site")
})
@ECIndexes({
        @ECIndex(unique=true, of={"account", "app", "name"}),
        @ECIndex(of={"account", "name"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppSite extends IdentifiableBase implements AppTemplateEntity {

    public static final String[] VALUE_FIELDS = {"template", "enabled", "description", "url"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS, "name", "app");

    public AppSite (AppSite other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) {
        copy(this, other, VALUE_FIELDS);
        return this;
    }

    @ECSearchable
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @ECIndex @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String name;

    @ECSearchable
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template () { return template == null || template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable(filter=true)
    @Column(nullable=false, length=10000)
    @Getter @Setter private String description;

    @ECSearchable(filter=true)
    @Column(nullable=false, length=1024)
    @Getter @Setter private String url;

}
