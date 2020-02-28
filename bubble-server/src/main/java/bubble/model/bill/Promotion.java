/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
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
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import java.util.Comparator;

import static bubble.ApiConstants.PROMOTIONS_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=PROMOTIONS_ENDPOINT, listFields={"name", "priority", "enabled", "validFrom", "validTo", "code", "referral"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class Promotion extends IdentifiableBase
        implements NamedEntity, HasPriority, SqlViewSearchResult, Comparable<Promotion> {

    public static final String[] UPDATE_FIELDS = {
            "priority", "enabled", "visible", "adminAssignOnly", "validFrom", "validTo"
    };
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS,
            "name", "code", "referral", "currency", "maxValue");

    public static final Comparator<? super Promotion> SORT_PAYMENT_METHOD_CTIME = (Comparator<Promotion>) (p1, p2) -> {
        if (p1.hasPaymentMethod() && p2.hasPaymentMethod()) {
            return Long.compare(p1.getPaymentMethod().getCtime(), p2.getPaymentMethod().getCtime());
        }
        return p1.compareTo(p2);
    };

    public Promotion (Promotion other) { copy(this, other, CREATE_FIELDS); }

    public Promotion(String id) {
        // used for finding Promotion in AccountPromotionsResource and PromotionService
        setUuid(id);
        setName(id);
    }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public int compareTo(Promotion o) {
        int diff = hasPriority() && o.hasPriority() ? getPriority().compareTo(o.getPriority()) : 0;
        return diff != 0 ? diff : Long.compare(getCtime(), o.getCtime());
    }

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
    public boolean hasPriority () { return priority != null; }

    @ECSearchable(filter=true) @ECField(index=40)
    @ECIndex(unique=true) @Column(updatable=false, length=NAME_MAXLEN)
    @Getter @Setter private String code;

    @ECSearchable @ECField(index=50)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean enabled = true;
    public boolean enabled () { return enabled == null || enabled; }
    public boolean disabled () { return !enabled(); }

    @ECSearchable @ECField(index=60)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean visible = false;
    public boolean visible() { return visible == null || visible; }
    public boolean invisible() { return !visible(); }

    @ECSearchable @ECField(index=70)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean adminAssignOnly = false;
    public boolean adminAssignOnly() { return adminAssignOnly != null && adminAssignOnly; }
    public boolean notAdminAssignOnly() { return !adminAssignOnly(); }

    @ECSearchable @ECField(index=80)
    @ECIndex @Getter @Setter private Long validFrom;
    public boolean hasStarted () { return validFrom == null || validFrom > now(); }

    @ECSearchable @ECField(index=90)
    @ECIndex @Getter @Setter private Long validTo;
    public boolean hasEnded () { return validTo != null && validTo > now(); }

    public boolean active () { return enabled() && hasStarted() && !hasEnded(); }
    public boolean inactive () { return !active(); }

    @ECSearchable @ECField(index=100)
    @ECIndex @Column(nullable=false)
    @Getter @Setter private Boolean referral = false;
    public boolean referral () { return referral != null && referral; }

    @ECSearchable @ECField(index=110)
    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    public boolean isCurrency(String currency) {
        return currency != null && currency.equalsIgnoreCase(this.currency);
    }

    @ECSearchable @ECField(index=120)
    @ECIndex @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer minValue = 100;

    @ECSearchable @ECField(index=130)
    @ECIndex @Column(nullable=false, updatable=false)
    @Getter @Setter private Integer maxValue;

    @Transient @Getter @Setter private AccountPaymentMethod paymentMethod;
    public boolean hasPaymentMethod () { return paymentMethod != null; }

}
