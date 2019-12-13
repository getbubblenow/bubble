package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECTypeFields;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECTypeURIs;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true) @ECTypeURIs(listFields={"account", "plan", "paymentMethod", "bill"})
@ECTypeFields(list={"account", "plan", "paymentMethod", "bill"})
@Entity @NoArgsConstructor @Accessors(chain=true)
public class AccountPayment extends IdentifiableBase implements HasAccountNoName {

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECForeignKey(entity=AccountPlanPaymentMethod.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String planPaymentMethod;

    @ECForeignKey(entity=Bill.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String bill;

    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Getter @Setter private AccountPaymentStatus status;

    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(100000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String info;

    @Transient @Getter @Setter private Bill billObject;

}
