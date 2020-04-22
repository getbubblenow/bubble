/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.payment.delegate;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.*;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class DelegatedPaymentDriver extends DelegatedCloudServiceDriverBase implements PaymentServiceDriver {

    @Autowired private CloudServiceDAO cloudDAO;

    public DelegatedPaymentDriver(CloudService cloud) { super(cloud); }

    @Override public PaymentMethodType getPaymentMethodType() {
        if (!cloud.delegated()) {
            log.warn("getPaymentMethodType: delegated driver has non-delegated cloud: "+cloud.getUuid());
            return cloud.getPaymentDriver(configuration).getPaymentMethodType();
        }
        if (!configuration.paymentsEnabled()) {
            log.warn("getPaymentMethodType: payments not enabled, returning null");
            return null;
        };
        final CloudService delegate = cloudDAO.findByUuid(cloud.getDelegated());
        if (delegate == null) throw invalidEx("err.paymentService.notFound");
        return delegate.getPaymentDriver(configuration).getPaymentMethodType();
    }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_validate,
                new PaymentMethodValidationNotification(cloud.getName(), paymentMethod));
    }

    @Override public PaymentValidationResult claim(AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_claim,
                new PaymentMethodClaimNotification(cloud.getName(), paymentMethod));
    }

    @Override public PaymentValidationResult claim(AccountPlan accountPlan) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_claim,
                new PaymentMethodClaimNotification(cloud.getName(), accountPlan));
    }

    @Override public boolean authorize(BubblePlan plan, String accountPlanUuid, String billUuid, AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        final PaymentResult result = notificationService.notifySync(delegate, payment_driver_authorize,
                new PaymentNotification()
                        .setCloud(cloud.getName())
                        .setPlanUuid(plan.getUuid())
                        .setAccountPlanUuid(accountPlanUuid)
                        .setBillUuid(billUuid)
                        .setPaymentMethodUuid(paymentMethod.getUuid()));
        return processResult(result);
    }

    @Override public boolean cancelAuthorization(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        final PaymentResult result = notificationService.notifySync(delegate, payment_driver_cancel_authorization,
                new PaymentNotification()
                        .setCloud(cloud.getName())
                        .setPlanUuid(plan.getUuid())
                        .setAccountPlanUuid(accountPlanUuid)
                        .setPaymentMethodUuid(paymentMethod.getUuid()));
        return processResult(result);
    }

    @Override public boolean purchase(String accountPlanUuid,
                                      String paymentMethodUuid,
                                      String billUuid) {
        final BubbleNode delegate = getDelegateNode();
        final PaymentResult result = notificationService.notifySync(delegate, payment_driver_purchase,
                new PaymentNotification()
                        .setCloud(cloud.getName())
                        .setAccountPlanUuid(accountPlanUuid)
                        .setPaymentMethodUuid(paymentMethodUuid)
                        .setBillUuid(billUuid));
        return processResult(result);
    }

    @Override public boolean refund(String accountPlanUuid) {
        final BubbleNode delegate = getDelegateNode();
        final PaymentResult result = notificationService.notifySync(delegate, payment_driver_refund,
                new PaymentNotification()
                        .setCloud(cloud.getName())
                        .setAccountPlanUuid(accountPlanUuid));
        return processResult(result);
    }

    public boolean processResult(PaymentResult result) {
        if (result.success()) return true;
        if (result.hasViolations()) {
            throw invalidEx(result.violationList());
        }
        if (result.hasError()) return die("processResult: "+result.getError());
        return false;
    }

}
