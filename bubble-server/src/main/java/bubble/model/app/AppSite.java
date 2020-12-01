/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static bubble.ApiConstants.EP_SITES;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_SITES, listFields={"name", "app", "description", "url"})
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

    public static final String[] VALUE_FIELDS = {
            "template", "enabled", "description", "url", "maxSecurityHosts", "enableMaxSecurityHosts"
    };
    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS, "name", "app");

    public AppSite (AppSite other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) {
        copy(this, other, VALUE_FIELDS);
        return this;
    }

    @ECSearchable(filter=true) @ECField(index=10)
    @ECIndex @Column(nullable=false, updatable=false, length=1000)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

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

    @ECSearchable(filter=true) @ECField(index=60)
    @Column(nullable=false, length=10000)
    @Getter @Setter private String description;

    // use opaque_string instead of default (http_url) because '*' is a valid value
    @ECSearchable(filter=true) @ECField(index=70, type=EntityFieldType.opaque_string)
    @Column(nullable=false, length=1024)
    @Getter @Setter private String url;

    // Json array of hosts that should always get maximum security
    @ECField(index=80, type=EntityFieldType.json_array)
    @Column(length=5000)
    @JsonIgnore @Getter @Setter private String maxSecurityHostsJson;

    @Transient public String[] getMaxSecurityHosts () { return empty(maxSecurityHostsJson) ? null : json(maxSecurityHostsJson, String[].class); }
    public AppSite setMaxSecurityHosts(String[] hosts) { return setMaxSecurityHostsJson(empty(hosts) ? null : json(hosts, COMPACT_MAPPER)); }
    public boolean hasMaxSecurityHosts () { return !empty(getMaxSecurityHosts()); }

    @ECField(index=90)
    @Getter @Setter private Boolean enableMaxSecurityHosts;
    public boolean enableMaxSecurityHosts() { return enableMaxSecurityHosts == null ? true : enableMaxSecurityHosts; }

}
