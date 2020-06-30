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
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
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
import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_MATCHERS, listFields={"name", "app", "fqdn", "urlRegex", "rule"})
@Entity @NoArgsConstructor @Accessors(chain=true) @ToString
@ECIndexes({
        @ECIndex(unique=true, of={"account", "app", "name"}),
        @ECIndex(of={"account", "name"}),
        @ECIndex(of={"account", "app"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AppMatcher extends IdentifiableBase implements AppTemplateEntity, HasPriority {

    public static final String[] VALUE_FIELDS = {"fqdn", "urlRegex", "template", "enabled", "priority"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(VALUE_FIELDS, "name", "site", "rule", "connCheck");

    public static final Pattern DEFAULT_CONTENT_TYPE_PATTERN = Pattern.compile("^text/html.*", Pattern.CASE_INSENSITIVE);
    public static final String WILDCARD_FQDN = "*";
    public static final String WILDCARD_URL = ".*";

    public AppMatcher(AppMatcher other) {
        copy(this, other, CREATE_FIELDS);
        setUuid(null);
    }

    @Override public Identifiable update(Identifiable other) { copy(this, other, VALUE_FIELDS); return this; }

    @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true) @ECField(index=20)
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubbleApp.class)
    @Column(nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=AppSite.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String site;

    @ECSearchable(filter=true) @ECField(index=50)
    @HasValue(message="err.fqdn.required")
    @Size(max=1024, message="err.fqdn.length")
    @ECIndex @Column(nullable=false, length=1024)
    @Getter @Setter private String fqdn;
    @JsonIgnore @Transient public boolean isWildcardFqdn () { return fqdn != null && fqdn.equals(WILDCARD_FQDN); }

    @ECSearchable(filter=true) @ECField(index=60)
    @HasValue(message="err.urlRegex.required")
    @Size(max=1024, message="err.urlRegex.length")
    @Type(type=ENCRYPTED_STRING) @Column(nullable=false, columnDefinition="varchar("+(1024+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String urlRegex;
    public boolean hasUrlRegex() { return !empty(urlRegex) && !urlRegex.equals(WILDCARD_URL); }

    @Transient @JsonIgnore public Pattern getUrlPattern() { return Pattern.compile(getUrlRegex()); }
    public boolean matchesUrl (String value) { return getUrlPattern().matcher(value).find(); }

    @ECSearchable(filter=true) @ECField(index=70)
    @Size(max=200, message="err.contentTypeRegex.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(200+ENC_PAD)+")")
    @Getter @Setter private String contentTypeRegex;
    public boolean hasContentTypeRegex() { return !empty(contentTypeRegex); }

    @Transient @JsonIgnore public Pattern getContentTypePattern () {
        return hasContentTypeRegex() ? Pattern.compile(getContentTypeRegex()) : DEFAULT_CONTENT_TYPE_PATTERN;
    }
    public boolean matchesContentType (String value) { return getContentTypePattern().matcher(value).find(); }

    @ECSearchable(filter=true) @ECField(index=80)
    @Size(max=2000, message="err.userAgentRegex.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(2000+ENC_PAD)+")")
    @Getter @Setter private String userAgentRegex;
    public boolean hasUserAgentRegex() { return !empty(userAgentRegex); }

    @Transient @JsonIgnore public Pattern getUserAgentPattern () {
        return hasUserAgentRegex() ? Pattern.compile(getUserAgentRegex()) : DEFAULT_CONTENT_TYPE_PATTERN;
    }
    public boolean matchesUserAgent (String value) { return getUserAgentPattern().matcher(value).find(); }

    @ECSearchable @ECField(index=90)
    @ECForeignKey(entity=AppRule.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String rule;

    @ECSearchable @ECField(index=100)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=110)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @ECSearchable @ECField(index=120, required=EntityFieldRequired.optional)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean connCheck;
    public boolean connCheck () { return bool(connCheck); }

    @ECSearchable @ECField(index=130, required=EntityFieldRequired.optional)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean requestCheck;
    public boolean requestCheck () { return bool(requestCheck); }

    @ECSearchable @ECField(index=130)
    @Column(nullable=false)
    @Getter @Setter private Integer priority = 0;

}
