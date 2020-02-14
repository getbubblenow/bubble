package bubble.model.bill;

import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.HasPriority;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.NamedEntity;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.Entity;

import static bubble.ApiConstants.PROMOTIONS_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=PROMOTIONS_ENDPOINT, listFields={"name", "priority", "enabled", "validFrom", "validTo", "code", "referral"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class Promotion extends IdentifiableBase implements NamedEntity, HasPriority {

    public static final String[] UPDATE_FIELDS = {"priority", "enabled", "validFrom", "validTo"};
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "name", "code", "referral");

    public Promotion (Promotion other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @ECSearchable(filter=true) @ECField(index=10)
    @HasValue(message="err.name.required")
    @ECIndex(unique=true) @Column(nullable=false, updatable=false, length=NAME_MAXLEN)
    @Getter @Setter private String name;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=CloudService.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String cloud;

    @ECSearchable @ECField(index=30) @Column(nullable=false)
    @ECIndex @Getter @Setter private Integer priority = 1;

    @ECSearchable(filter=true) @ECField(index=40)
    @ECIndex(unique=true) @Column(updatable=false, length=NAME_MAXLEN)
    @Getter @Setter private String code;

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }

    @ECSearchable @ECField(index=60)
    @ECIndex @Getter @Setter private Long validFrom;
    public boolean hasStarted () { return validFrom == null || validFrom > now(); }

    @ECSearchable @ECField(index=70)
    @ECIndex @Getter @Setter private Long validTo;
    public boolean hasEnded () { return validTo != null && validTo > now(); }

    public boolean active () { return enabled() && hasStarted() && !hasEnded(); }
    public boolean inactive () { return !active(); }

    @ECSearchable @ECField(index=80)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean referral = false;
    public boolean referral () { return referral != null && referral; }

}
