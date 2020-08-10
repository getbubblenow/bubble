/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.model.bill.PaymentMethodType;
import bubble.notify.payment.PaymentValidationResult;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.hibernate.criterion.Restrictions.*;

@Repository @Slf4j
public class AccountPaymentMethodDAO extends AccountOwnedEntityDAO<AccountPaymentMethod> {

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    public AccountPaymentMethod findByAccountAndPaymentInfo(String account, String paymentInfo) {
        return findByAccount(account).stream()
                .filter(apm -> apm.getPaymentInfo().equals(paymentInfo))
                .findFirst()
                .orElse(null);
    }

    public List<AccountPaymentMethod> findByAccountAndPromoAndNotDeleted(String account) {
        return findByFields("account", account, "paymentMethodType", PaymentMethodType.promotional_credit, "deleted", null);
    }

    public List<AccountPaymentMethod> findByAccountAndNotPromoAndNotDeleted(String account) {
        return list(criteria().add(and(
                eq("account", account),
                ne("paymentMethodType", PaymentMethodType.promotional_credit),
                isNull("deleted")
        )));
    }

    public List<AccountPaymentMethod> findByAccountAndCloud(String accountUuid, String cloud) {
        return findByFields("account", accountUuid, "cloud", cloud);
    }

    @Override public Object preCreate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType().requiresClaim()) {
            final CloudService paymentService = cloudDAO.findByUuid(paymentMethod.getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");
            final PaymentValidationResult result = paymentService.getPaymentDriver(configuration).claim(paymentMethod);
            if (result.hasErrors()) throw invalidEx(result.violationsList());
        }
        return super.preCreate(paymentMethod);
    }

    @Override public void delete(String uuid) {
        final AccountPaymentMethod pm = findByUuid(uuid);
        if (pm == null) return;

        // ensure payment method is not in use by any account plan
        final List<AccountPlan> plans = accountPlanDAO.findByAccountAndPaymentMethodAndNotDeleted(pm.getAccount(), pm.getUuid());
        if (!plans.isEmpty()) {
            throw invalidEx("err.paymentMethod.cannotDeleteInUse");
        }

        update(pm.setDeleted().setPaymentInfo("deleted_"+now()));
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

}
