package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.EP_MESSAGES;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_MESSAGES, listFields={"app", "locale"})
@ECIndexes({
        @ECIndex(unique=true, of={"app", "locale"}),
        @ECIndex(of={"app", "priority"})
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AppMessage extends IdentifiableBase implements AccountTemplate, HasPriority {

    public static final String[] UPDATE_FIELDS = {"messages", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "app", "locale");

    public AppMessage (AppMessage other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(fkDepth=ECForeignKeySearchDepth.shallow) @ECField(index=20)
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable @ECField(index=30) @ECIndex
    @Size(max=20, message="err.locale.length")
    @Column(length=20, nullable=false, updatable=false)
    @Getter @Setter private String locale;
    public boolean hasLocale () { return !empty(locale); }

    @ECSearchable @ECField(index=40) @Column(length=100000, nullable=false)
    @JsonIgnore @Getter @Setter private String messagesJson;

    @Transient public NameAndValue[] getMessages () { return messagesJson == null ? null : json(messagesJson, NameAndValue[].class); }
    public AppMessage setMessages(NameAndValue[] messages) { return setMessagesJson(messages == null ? null : json(messages));}

    @ECSearchable @ECField(index=50) @Column(nullable=false)
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = true;

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @Transient @JsonIgnore @Override public String getName() { return getLocale(); }

}
