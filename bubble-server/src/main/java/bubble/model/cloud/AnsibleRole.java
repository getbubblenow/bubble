package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.*;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Arrays;

import static bubble.ApiConstants.DB_JSON_MAPPER;
import static bubble.ApiConstants.EP_ROLES;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true, name="role")
@ECTypeURIs(baseURI=EP_ROLES, listFields={"account", "name", "description"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class AnsibleRole extends IdentifiableBase implements AccountTemplate, HasPriority {

    public static final String[] COPY_FIELDS = {
            "name", "priority", "description", "template", "enabled",
            "configJson", "optionalConfigNamesJson", "tgzB64"
    };
    public static final String ROLENAME_PATTERN = "[_A-Za-z0-9]+-[\\d]+\\.[\\d]+\\.[\\d]+";

    public AnsibleRole(AnsibleRole role) { copy(this, role, COPY_FIELDS); }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @Pattern(regexp=ROLENAME_PATTERN, message="err.name.invalid")
    @ECIndex @Column(nullable=false, updatable=false, length=200)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @JsonIgnore @Transient @Getter(lazy=true) private final String roleName = getRoleName(name);
    @JsonIgnore @Transient @Getter(lazy=true) private final SemanticVersion version = getRoleVersion(name);

    public static String getRoleName(String name) {
        final int lastHyphen = name.lastIndexOf('-');
        return lastHyphen == -1 ? name : name.substring(0, lastHyphen);
    }
    public static boolean sameRoleName(String r1, String r2) { return getRoleName(r1).equals(getRoleName(r2)); }

    public static SemanticVersion getRoleVersion(String name) {
        final int lastHyphen = name.lastIndexOf('-');
        return lastHyphen == -1 ? null : new SemanticVersion(name.substring(lastHyphen +1));
    }

    public static String findRole(String[] roles, String role) {
        if (roles == null) return null;
        return Arrays.stream(roles)
                .filter(r -> sameRoleName(role, r))
                .findAny()
                .orElse(null);
    }

    @ECSearchable(filter=true) @ECField(index=30)
    @Size(max=10000, message="err.description.length")
    @Getter @Setter private String description;

    @ECSearchable @ECField(index=40)
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(nullable=false, length=20)
    @Getter @Setter private AnsibleInstallType install = AnsibleInstallType.standard;
    public boolean shouldInstall(AnsibleInstallType installType) {
        return install.shouldInstall(installType);
    }

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Integer priority;

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;

    @Column(updatable=false, length=10000)
    @JsonIgnore @Getter @Setter private String configJson;
    public boolean hasConfig () { return configJson != null; }

    @Transient public NameAndValue[] getConfig () { return configJson == null ? null : json(configJson, NameAndValue[].class); }
    public AnsibleRole setConfig(NameAndValue[] config) { return setConfigJson(config == null ? null : json(config, DB_JSON_MAPPER)); }

    @Column(updatable=false, length=1000) @ECField(index=80)
    @JsonIgnore @Getter @Setter private String optionalConfigNamesJson;
    public boolean hasOptionalConfigNames () { return optionalConfigNamesJson != null; }

    @Transient public String[] getOptionalConfigNames() { return optionalConfigNamesJson == null ? null : json(optionalConfigNamesJson, String[].class); }
    public AnsibleRole setOptionalConfigNames(String[] names) { return setOptionalConfigNamesJson(name == null ? null : json(names, DB_JSON_MAPPER)); }

    // The Base64-encoded .tgz archive for the role directory. all paths should start with roles/<role-name>/...
    // Then after it is stored (in AnsibleRoleDAO.preCreate), this becomes storage://CloudServiceName/path
    @Column(updatable=false, length=200)
    @Getter @Setter private String tgzB64;
    public boolean hasTgzB64 () { return tgzB64 != null; }

    @Transient @JsonIgnore public boolean isTgzB64raw() { return tgzB64 != null && !tgzB64.startsWith(STORAGE_PREFIX); }
    @Transient @JsonIgnore public boolean isTgzB64storage() { return tgzB64 != null && tgzB64.startsWith(STORAGE_PREFIX); }

    public boolean isOptionalConfigName(String cfgName) {
        final String[] names = getOptionalConfigNames();
        if (names == null) return false;
        return Arrays.asList(names).contains(cfgName);
    }
}
