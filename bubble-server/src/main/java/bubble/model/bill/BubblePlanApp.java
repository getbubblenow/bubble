/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import bubble.model.app.BubbleApp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static bubble.ApiConstants.EP_APPS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(baseURI=EP_APPS, listFields={"plan", "app"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"plan", "app"})
})
public class BubblePlanApp extends IdentifiableBase implements HasAccountNoName {

    public static final String[] CREATE_FIELDS = {"plan", "app"};

    public BubblePlanApp (BubblePlanApp other) { copy(this, other, CREATE_FIELDS); }

    // updates not allowed
    @Override public Identifiable update(Identifiable thing) { return this; }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity= BubbleApp.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String app;

    @Transient @Getter @Setter private transient BubbleApp appObject;

}
