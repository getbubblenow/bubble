package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccountNoName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@ECType(root=true) @ECTypeURIs(listFields={"name", "type", "quantity", "price", "period"})
@ECTypeFields(list={"name", "type", "quantity", "price", "period"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "plan", "type", "period"})
})
public class Bill extends IdentifiableBase implements HasAccountNoName {

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=AccountPlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECIndex @Enumerated(EnumType.STRING)
    @Column(nullable=false, updatable=false, length=20)
    @Getter @Setter private BillItemType type;

    @Column(nullable=false, updatable=false, length=10)
    @ECIndex @Getter @Setter private String period;

    @Type(type=ENCRYPTED_LONG) @Column(updatable=false, columnDefinition="varchar("+(ENC_LONG)+") NOT NULL")
    @Getter @Setter private Long quantity = 0L;

    @Type(type=ENCRYPTED_INTEGER) @Column(updatable=false, columnDefinition="varchar("+(ENC_INT)+") NOT NULL")
    @Getter @Setter private Integer price = 0;

    @ECIndex @Column(nullable=false, updatable=false, length=10)
    @Getter @Setter private String currency;

    @JsonIgnore @Transient public long getTotal() { return big(quantity).multiply(big(price)).longValue(); }

}
