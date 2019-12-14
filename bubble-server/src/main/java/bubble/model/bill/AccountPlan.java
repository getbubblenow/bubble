package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true)
@ECTypeURIs(listFields={"account", "plan", "network", "name"})
@ECTypeFields(list={"account", "plan", "network", "name"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}),
        @ECIndex(unique=true, of={"account", "network"}),
        @ECIndex(unique=true, of={"plan", "network"})
})
public class AccountPlan extends IdentifiableBase implements HasAccount {

    public static final String[] CREATE_FIELDS = {
            "name", "description", "locale", "timezone", "domain", "network", "plan", "footprint", "paymentMethod"
    };

    @SuppressWarnings("unused")
    public AccountPlan (AccountPlan other) { copy(this, other, CREATE_FIELDS); }

    // mirrors network name
    @Size(max=100, message="err.name.length")
    @Column(length=100, nullable=false)
    @Getter @Setter private String name;

    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECForeignKey(entity=BubbleDomain.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;

    @ECForeignKey(entity=BubbleNetwork.class, index=false) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @Column(nullable=false)
    @Getter @Setter private Boolean enabled = false;
    public boolean enabled() { return enabled != null && enabled; }

    @ECIndex(unique=true) @Column(length=UUID_MAXLEN)
    @Getter @Setter private String deletedNetwork;
    public boolean deleted() { return deletedNetwork != null; }

    // Fields below are used when creating a new plan, to also create the network associated with it
    @Size(max=10000, message="err.description.length")
    @Transient @Getter @Setter private transient String description;

    @Transient @Getter @Setter private transient String locale = null;
    public boolean hasLocale () { return locale != null; }

    @Transient @Getter @Setter private transient String timezone = null;
    public boolean hasTimezone () { return timezone != null; }

    @Transient @Getter @Setter private transient String footprint = null;
    public boolean hasFootprint () { return footprint != null; }

    @Transient @Getter @Setter private transient AccountPaymentMethod paymentMethod = null;
    public boolean hasPaymentMethod () { return paymentMethod != null; }

    public BubbleNetwork bubbleNetwork(Account account,
                                       BubbleDomain domain,
                                       BubblePlan plan,
                                       CloudService storage) {
        return new BubbleNetwork()
                .setName(getName())
                .setDescription(getDescription())
                .setLocale(getLocale())
                .setTimezone(getTimezone())
                .setAccount(account.getUuid())
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setFootprint(getFootprint())
                .setComputeSizeType(plan.getComputeSizeType())
                .setStorage(storage.getUuid());
    }

}
