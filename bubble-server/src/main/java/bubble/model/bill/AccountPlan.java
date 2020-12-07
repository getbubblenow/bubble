/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.bill;

import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.account.HasNetwork;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.LaunchType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import static bubble.model.bill.BillPeriod.BILL_START_END_FORMAT;
import static bubble.model.cloud.BubbleNetwork.NETWORK_NAME_MAXLEN;
import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@ECType(root=true) @ECTypeCreate(method="DISABLED")
@ECTypeURIs(listFields={"account", "plan", "network", "name"})
@Entity @NoArgsConstructor @Accessors(chain=true) @Slf4j
@ECIndexes({
        @ECIndex(unique=true, of={"account", "name"}, where="deleted IS NULL"),
        @ECIndex(unique=true, of={"account", "network"}),
        @ECIndex(unique=true, of={"plan", "network"})
})
public class AccountPlan extends IdentifiableBase implements HasNetwork {

    public static final String[] UPDATE_FIELDS = {"description", "paymentMethod", "paymentMethodObject"};

    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS,
            "name", "launchType", "forkHost", "adminEmail", "locale", "timezone", "domain", "network",
            "sshKey", "syncAccount", "launchLock", "sendErrors", "sendMetrics", "plan", "footprint");

    @SuppressWarnings("unused")
    public AccountPlan (AccountPlan other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Override public void beforeCreate() { if (!hasUuid()) initUuid(); }

    // mirrors network name
    @ECSearchable(filter=true) @ECField(index=10)
    @Size(max=NETWORK_NAME_MAXLEN, message="err.name.length")
    @Column(length=NETWORK_NAME_MAXLEN, nullable=false)
    @Getter private String name;
    public AccountPlan setName (String name) { this.name = name == null ? null : name.toLowerCase(); return this; }

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=30)
    @ECForeignKey(entity=BubblePlan.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String plan;

    @ECSearchable @ECField(index=40)
    @ECForeignKey(entity=AccountPaymentMethod.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String paymentMethod;

    @ECSearchable @ECField(index=50)
    @ECForeignKey(entity=BubbleDomain.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String domain;

    @ECSearchable @ECField(index=60)
    @ECForeignKey(entity=BubbleNetwork.class, index=false) @ECIndex(unique=true)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @ECSearchable @ECField(index=70)
    @ECForeignKey(entity=AccountSshKey.class)
    @Column(length=UUID_MAXLEN)
    @Getter @Setter private String sshKey;
    public boolean hasSshKey () { return !empty(sshKey); }

    @ECSearchable @ECField(index=80)
    @Column(nullable=false)
    @Getter @Setter private Boolean enabled = false;
    public boolean enabled() { return bool(enabled); }
    public boolean disabled() { return !enabled(); }

    @ECSearchable(type=EntityFieldType.epoch_time) @ECField(index=90)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Long nextBill;

    @ECSearchable @ECField(index=100)
    @Column(nullable=false, length=50)
    @Getter @Setter private String nextBillDate;
    public AccountPlan setNextBillDate() { return setNextBillDate(BILL_START_END_FORMAT.print(getNextBill())); }

    @ECSearchable @ECField(index=110) @Column(nullable=false)
    @ECIndex @Getter @Setter private Boolean deleting;
    public boolean deleting() { return bool(deleting); }
    public boolean notDeleting() { return !deleting(); }

    @ECSearchable @ECField(index=120)
    @ECIndex @Getter @Setter private Long deleted;
    public boolean deleted() { return deleted != null; }
    public boolean notDeleted() { return !deleted(); }

    @ECSearchable @ECField(index=130)
    @Column(nullable=false)
    @ECIndex @Getter @Setter private Boolean closed = false;
    public boolean closed() { return bool(closed); }
    public boolean notClosed() { return !closed(); }

    @ECSearchable @ECField(index=140, type=EntityFieldType.reference)
    @ECIndex(unique=true) @Column(length=UUID_MAXLEN)
    @Getter @Setter private String deletedNetwork;
    public boolean hasDeletedNetwork() { return deletedNetwork != null; }

    @ECSearchable @ECField(index=150) @Column(nullable=false)
    @Getter @Setter private Boolean refundIssued = false;

    @ECSearchable @ECField(index=160, type=EntityFieldType.error)
    @Getter @Setter private String refundError;

    // Fields below are used when creating a new plan, to also create the network associated with it
    @Size(max=NAME_MAXLEN, message="err.nick.tooLong")
    @Transient @Getter @Setter private transient String nickname;
    public boolean hasNickname () { return !empty(nickname); }

    @Size(max=10000, message="err.description.length")
    @Transient @Getter @Setter private transient String description;

    @Transient @Getter @Setter private transient String locale = null;
    public boolean hasLocale () { return !empty(locale); }

    @Transient @Getter @Setter private transient String timezone = null;
    public boolean hasTimezone () { return !empty(timezone); }

    @Transient @Getter @Setter private transient String footprint = null;
    public boolean hasFootprint () { return !empty(footprint); }

    @Transient @Getter @Setter private transient AccountPaymentMethod paymentMethodObject = null;
    public boolean hasPaymentMethodObject () { return paymentMethodObject != null; }

    @JsonIgnore @Transient @Getter @Setter private transient Account accountObject = null;
    public boolean hasAccountObject () { return account != null; }

    @Transient @Getter @Setter private transient LaunchType launchType = null;
    public boolean hasLaunchType () { return launchType != null; }

    @Transient @Getter @Setter private transient String forkHost = null;
    public boolean hasForkHost () { return !empty(forkHost); }

    @Transient @Getter @Setter private transient String adminEmail = null;
    public boolean hasAdminEmail() { return !empty(adminEmail); }

    @Transient @Getter @Setter private transient Boolean syncAccount = null;
    public boolean syncAccount() { return syncAccount == null || syncAccount; }

    @Transient @Getter @Setter private Boolean launchLock;
    public boolean launchLock() { return bool(launchLock); }

    @Transient @Getter @Setter private transient Boolean sendErrors = null;
    public boolean sendErrors () { return sendErrors == null || sendErrors; }

    @Transient @Getter @Setter private transient Boolean sendMetrics = null;
    public boolean sendMetrics () { return sendMetrics == null || sendMetrics; }

    public BubbleNetwork bubbleNetwork(Account account,
                                       BubbleDomain domain,
                                       BubblePlan plan,
                                       CloudService storage) {
        return new BubbleNetwork()
                .setName(getName())
                .setNickname(getNickname())
                .setDescription(getDescription())
                .setLocale(getLocale())
                .setTimezone(getTimezone())
                .setAccount(account.getUuid())
                .setSshKey(getSshKey())
                .setSyncAccount(syncAccount())
                .setLaunchLock(launchLock())
                .setSendErrors(sendErrors())
                .setSendMetrics(sendMetrics())
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setFootprint(getFootprint())
                .setComputeSizeType(plan.getComputeSizeType())
                .setStorage(storage.getUuid())
                .setLaunchType(hasLaunchType() ? getLaunchType() : LaunchType.node)
                .setForkHost(hasForkHost() ? getForkHost() : null)
                .setAdminEmail(hasAdminEmail() ? getAdminEmail() : null);
    }

}
