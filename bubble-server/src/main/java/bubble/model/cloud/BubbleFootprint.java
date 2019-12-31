package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.AccountTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static bubble.ApiConstants.EP_FOOTPRINTS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_FOOTPRINTS, listFields={"name", "description"})
@ECTypeFields(list={"name", "description"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(of={"account", "template", "enabled"}),
        @ECIndex(of={"template", "enabled"})
})
public class BubbleFootprint extends IdentifiableBase implements AccountTemplate {

    public static final String[] UPDATE_FIELDS = {"description", "template", "enabled"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name",
            "allowedCountriesJson", "disallowedCountriesJson");

    public static final Function<String, String> NORMALIZE_COUNTRY = String::toUpperCase;

    public static final String DEFAULT_FOOTPRINT = "Worldwide";

    public static final BubbleFootprint DEFAULT_FOOTPRINT_OBJECT = new BubbleFootprint()
            .setName(DEFAULT_FOOTPRINT)
            .setDescription("Exclude countries subject to United States OFAC sanctions")
            .setTemplate(true)
            .setDisallowedCountries(new String[] {"IR", "KP", "SY", "SD", "CU", "VE"});

    @SuppressWarnings("unused")
    public BubbleFootprint (BubbleFootprint other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable(filter=true)
    @ECIndex @Column(nullable=false, updatable=false, length=100)
    @Getter @Setter private String name;

    @ECSearchable(filter=true)
    @Size(max=10000, message="err.description.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(10000+ENC_PAD)+")")
    @Getter @Setter private String description;

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean template = false;
    public boolean template() { return template != null && template; }

    @ECSearchable
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable
    @Size(max=5000, message="err.allowedCountriesJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(5000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String allowedCountriesJson;
    public boolean hasAllowedCountries () { return allowedCountriesJson != null; }

    @Transient public String[] getAllowedCountries () { return allowedCountriesJson == null ? null : json(allowedCountriesJson, String[].class); }
    public BubbleFootprint setAllowedCountries (String[] countries) { return setAllowedCountriesJson(countries == null ? null : json(countries)); }

    @ECSearchable
    @Size(max=5000, message="err.disallowedCountriesJson.length")
    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+(5000+ENC_PAD)+")")
    @JsonIgnore @Getter @Setter private String disallowedCountriesJson;
    public boolean hasDisallowedCountries () { return disallowedCountriesJson != null; }

    @Transient public String[] getDisallowedCountries () { return disallowedCountriesJson == null ? null : json(disallowedCountriesJson, String[].class); }
    public BubbleFootprint setDisallowedCountries (String[] countries) { return setDisallowedCountriesJson(countries == null ? null : json(countries)); }

    public void addDisallowedCountries(String[] countries) {
        final Set<String> disallowed = hasDisallowedCountries() ? new HashSet<>(Arrays.asList(getDisallowedCountries())) : new HashSet<>();
        disallowed.addAll(Arrays.asList(countries));
        setDisallowedCountries(new ArrayList<>(disallowed).toArray(new String[0]));
    }

    public boolean isAllowedCountry (String country) {
        final String[] allowed = getAllowedCountries();
        final String[] disallowed = getDisallowedCountries();
        final String c = NORMALIZE_COUNTRY.apply(country);
        if (hasDisallowedCountries() && Arrays.asList(disallowed).contains(c)) return false;
        if (hasAllowedCountries()    && Arrays.asList(allowed).contains(c))    return true;

        // if allowed list is empty, it is allowed. if allowed list is non-empty, it must be on the list
        return allowed == null || allowed.length == 0;
    }

}
