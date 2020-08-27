/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static bubble.ApiConstants.EP_MESSAGES;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKeySearchDepth.shallow;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_MESSAGES, listFields={"app", "locale"})
@ECIndexes({
        @ECIndex(unique=true, of={"app", "locale"}),
        @ECIndex(of={"app", "priority"})
})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AppMessage extends IdentifiableBase implements AppTemplateEntity, HasPriority {

    public static final String[] UPDATE_FIELDS = {"messages", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "app", "locale");

    public AppMessage (AppMessage other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public <T extends AppTemplateEntity> void upgrade(T sageObject, BubbleConfiguration configuration) {
        final Map<String, String> map = NameAndValue.toMap(getMessages());
        map.putAll(NameAndValue.toMap(((AppMessage) sageObject).getMessages()));
        setMessagesJson(json(NameAndValue.map2list(map)));
    }

    @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(fkDepth=shallow) @ECField(index=20)
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable @ECField(index=30) @ECIndex
    @Size(max=20, message="err.locale.length")
    @Column(length=20, nullable=false, updatable=false)
    @Getter @Setter private String locale;
    public boolean hasLocale () { return !empty(locale); }

    @Size(max=100000, message="err.appMessages.length")
    @ECSearchable @ECField(index=40) @Column(length=100000, nullable=false)
    @JsonIgnore @Getter @Setter private String messagesJson;

    @Transient public NameAndValue[] getMessages () { return messagesJson == null ? null : json(messagesJson, NameAndValue[].class); }
    public AppMessage setMessages(NameAndValue[] messages) { return setMessagesJson(messages == null ? null : json(messages, DB_JSON_MAPPER));}

    public String getMessage(String name) { return NameAndValue.find(getMessages(), name); }

    public NameAndValue[] getMessages (Collection<String> find) {
        return Arrays.stream(getMessages())
                .filter(m -> find.contains(m.getName()))
                .toArray(NameAndValue[]::new);
    }

    public boolean hasMessage(String name) { return !empty(getMessage(name)); }

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
