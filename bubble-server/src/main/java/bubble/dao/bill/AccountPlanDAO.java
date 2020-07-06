/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.HostnameValidationResult;
import bubble.notify.payment.PaymentValidationResult;
import bubble.server.BubbleConfiguration;
import bubble.service.bill.RefundService;
import bubble.service.cloud.NetworkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.model.cloud.BubbleNetwork.validateHostname;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.hibernate.criterion.Restrictions.*;

@Repository @Slf4j
public class AccountPlanDAO extends AccountOwnedEntityDAO<AccountPlan> {

    public static final long PURCHASE_DELAY = SECONDS.toMillis(3);

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private BillDAO billDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private NetworkService networkService;
    @Autowired private RefundService refundService;
    @Autowired private BubbleConfiguration configuration;

    @Override public AccountPlan findByAccountAndId(String accountUuid, String id) {
        final AccountPlan byUuid = findByUniqueFields("account", accountUuid, "uuid", id);
        if (byUuid != null) return byUuid;
        return findByAccountAndIdAndNotDeleted(accountUuid, id);
    }

    public AccountPlan findByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByUniqueFields("account", accountUuid, "network", networkUuid);
    }

    public AccountPlan findByAccountAndNetworkAndNotDeleted(String accountUuid, String networkUuid) {
        return findByUniqueFields("account", accountUuid, "network", networkUuid, "deleting", false, "deleted", null);
    }

    public AccountPlan findByNetwork(String networkUuid) { return findByUniqueField("network", networkUuid); }

    public List<AccountPlan> findByAccountAndNotDeleted(String account) {
        return findByFields("account", account, "deleting", false, "deleted", null);
    }

    public AccountPlan findByAccountAndIdAndNotDeleted(String account, String id) {
        final AccountPlan accountPlan = findByUniqueFields("account", account, "uuid", id, "deleting", false, "deleted", null);
        return accountPlan != null ? accountPlan : findByUniqueFields("account", account, "name", id, "deleting", false, "deleted", null);
    }

    public List<AccountPlan> findByAccountAndPaymentMethodAndNotDeleted(String account, String paymentMethod) {
        return findByFields("account", account, "paymentMethod", paymentMethod, "deleting", false, "deleted", null);
    }

    public List<AccountPlan> findByDeletedAndNotClosedAndNoRefundIssued() {
        return list(criteria().add(and(
                isNotNull("deleted"),
                eq("closed", false),
                eq("refundIssued", false)
        )));
    }

    public List<AccountPlan> findBillableAccountPlans(long time) {
        return list(criteria().add(and(
                isNull("deleted"),
                eq("closed", false),
                le("nextBill", time)
        )));
    }

    public boolean isNotDeleted(String networkUuid) {
        final AccountPlan accountPlan = findByNetwork(networkUuid);
        return accountPlan != null && accountPlan.notDeleted() && accountPlan.notDeleted();
    }

    @Override public Object preCreate(AccountPlan accountPlan) {
        final HostnameValidationResult errors = validateHostname(accountPlan, accountDAO, networkDAO);
        if (errors.isInvalid()) throw invalidEx(errors);
        if (errors.hasSuggestedName()) accountPlan.setName(errors.getSuggestedName());

        if (configuration.paymentsEnabled()) {
            if (!accountPlan.hasPaymentMethodObject()) throw invalidEx("err.paymentMethod.required");
            if (!accountPlan.getPaymentMethodObject().hasUuid()) throw invalidEx("err.paymentMethod.required");

            if (accountPlan.getPaymentMethod() == null) {
                accountPlan.setPaymentMethod(accountPlan.getPaymentMethodObject().getUuid());
            }

            final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethodObject().getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");

            final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
            if (paymentDriver.getPaymentMethodType().requiresClaim()) {
                final PaymentValidationResult result = paymentDriver.claim(accountPlan);
                if (result.hasErrors()) throw invalidEx(result.violationsList());
            }
            if (paymentDriver.getPaymentMethodType().requiresAuth()) {
                final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
                accountPlan.beforeCreate(); // ensure uuid exists
                paymentDriver.authorize(plan, accountPlan.getUuid(), null, accountPlan.getPaymentMethodObject());
            }
            accountPlan.setPaymentMethod(accountPlan.getPaymentMethodObject().getUuid());
            accountPlan.setNextBill(0L); // bill and payment occurs in postCreate, will update this
            accountPlan.setNextBillDate("msg.nextBillDate.pending");
        } else {
            accountPlan.setNextBill(Long.MAX_VALUE);
            accountPlan.setNextBillDate("msg.nextBillDate.paymentsNotEnabled");
        }

        accountPlan.setDeleting(false);
        return super.preCreate(accountPlan);
    }

    @Override public AccountPlan postCreate(AccountPlan accountPlan, Object context) {
        if (configuration.paymentsEnabled()) {
            final String accountPlanUuid = accountPlan.getUuid();
            final String paymentMethodUuid = accountPlan.getPaymentMethodObject().getUuid();
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            final Bill bill = billDAO.createFirstBill(plan, accountPlan);
            final String billUuid = bill.getUuid();

            // set nextBill to be just after the current bill period ends
            accountPlan.setNextBill(plan.getPeriod().periodMillis(bill.getPeriodEnd()));
            accountPlan.setNextBillDate();
            update(accountPlan);

            final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethodObject().getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");

            final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
            background(() -> {
                sleep(PURCHASE_DELAY, "AccountPlanDAO.postCreate: waiting to finalize purchase");
                paymentDriver.purchase(accountPlanUuid, paymentMethodUuid, billUuid);
            });
        }
        return super.postCreate(accountPlan, context);
    }

    @Override public void delete(String uuid) {
        final AccountPlan accountPlan = findByUuid(uuid);
        if (accountPlan == null) return;

        final BubbleNetwork network = networkDAO.findByUuid(accountPlan.getNetwork());
        if (network != null && network.getState() != BubbleNetworkState.stopped) {
            try {
                networkService.stopNetwork(network);
            } catch (Exception e) {
                log.warn("delete: error stopping network: "+shortError(e));
            }
        }
        update(accountPlan.setDeleted(now()).setEnabled(false));
        if (accountPlan.getNetwork() == null && accountPlan.getDeletedNetwork() != null) {
            log.warn("delete: network was supposed to be deleted, deleting it again: "+accountPlan.getDeletedNetwork());
            networkDAO.delete(accountPlan.getDeletedNetwork());
        } else {
            networkDAO.delete(accountPlan.getNetwork());
            if (configuration.paymentsEnabled()) {
                refundService.processRefunds();
            }
        }
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

}
