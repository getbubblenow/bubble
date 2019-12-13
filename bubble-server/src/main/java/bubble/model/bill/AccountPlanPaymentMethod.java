package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndexes;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECType;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity @ECType
@NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(of={"accountPlan", "paymentMethod"}) })
public class AccountPlanPaymentMethod extends IdentifiableBase implements HasAccountNoName {

    @Override public void beforeCreate() { if (!hasUuid()) initUuid(); }

    @ECForeignKey(entity=Account.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String account;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String accountPlan;

    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(length=UUID_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String paymentMethod;

    @Column(nullable=false)
    @Getter @Setter private Boolean deleted = false;
    public boolean deleted() { return deleted != null && deleted; }

}
