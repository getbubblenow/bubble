package bubble.service.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.bill.*;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.NetworkService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_DESC;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class BillingService extends SimpleDaemon {

    private static final long BILLING_CHECK_INTERVAL = HOURS.toMillis(6);
    private static final int MAX_UNPAID_DAYS_BEFORE_STOP = 7;

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BillDAO billDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private NetworkService networkService;
    @Autowired private BubbleConfiguration configuration;

    public void processBilling () { interrupt(); }

    @Override protected long getSleepTime() { return BILLING_CHECK_INTERVAL; }

    @Override protected boolean canInterruptSleep() { return true; }

    @Override protected void process() {
        // sort plans by Account ctime, newer Accounts are billed before older Accounts
        final List<AccountPlan> plansToBill = accountPlanDAO.findBillableAccountPlans(now());
        final Map<Account, List<AccountPlan>> plansByAccount = new TreeMap<>(CTIME_DESC);
        for (AccountPlan accountPlan : plansToBill) {
            final Account account = accountDAO.findByUuid(accountPlan.getAccount());
            if (account == null) {
                reportError("process: account "+accountPlan.getAccount()+" not found for AccountPlan="+accountPlan.getUuid());
            } else {
                plansByAccount.computeIfAbsent(account, a -> new ArrayList<>()).add(accountPlan);
            }
        }

        for (Account account : plansByAccount.keySet()) {
            for (AccountPlan accountPlan : plansByAccount.get(account)) {

                final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
                if (plan == null) {
                    final String msg = "process: plan not found (" + accountPlan.getPlan() + ") for accountPlan: " + accountPlan.getUuid();
                    log.error(msg);
                    reportError("BillingService: "+msg);
                    continue;
                }

                final List<Bill> bills;
                try {
                    bills = billPlan(plan, accountPlan);
                } catch (Exception e) {
                    log.error("process: error creating bill(s) for accountPlan "+accountPlan.getUuid()+": "+e);
                    continue;
                }

                boolean sendPaymentReminder = false;
                try {
                    if (!payBills(plan, accountPlan, bills)) {
                        log.error("process: payBills returned false for "+bills.size()+" bill(s) for accountPlan "+accountPlan.getUuid());
                        sendPaymentReminder = true;
                    }
                } catch (Exception e) {
                    log.error("process: error paying "+bills.size()+" bill(s) for accountPlan "+accountPlan.getUuid()+": "+e);
                    sendPaymentReminder = true;
                }

                if (sendPaymentReminder) {
                    // plan has unpaid bill, try again tomorrow
                    accountPlan.setNextBill(new DateTime(now()).plusDays(1).getMillis());
                    accountPlan.setNextBillDate();
                    accountPlanDAO.update(accountPlan);

                    final Bill bill = bills.get(0);
                    final long unpaidStart = plan.getPeriod().periodMillis(bill.getPeriodStart());
                    final int unpaidDays = Days.daysBetween(new DateTime(unpaidStart), new DateTime(now())).getDays();
                    final BubbleNetwork network = networkDAO.findByUuid(accountPlan.getNetwork());
                    if (unpaidDays > MAX_UNPAID_DAYS_BEFORE_STOP) {
                        accountPlanDAO.update(accountPlan.setEnabled(false));
                        try {
                            networkService.stopNetwork(network);
                        } catch (Exception e) {
                            final String msg = "process: error stopping network due to non-payment: " + network.getUuid();
                            log.error(msg);
                            reportError("BillingService: "+msg);
                            continue;
                        }
                        messageDAO.create(new AccountMessage()
                                .setAccount(accountPlan.getAccount())
                                .setNetwork(network.getUuid())
                                .setMessageType(AccountMessageType.notice)
                                .setTarget(ActionTarget.network)
                                .setAction(AccountAction.payment)
                                .setName(accountPlan.getUuid())
                                .setData(accountPlan.getNetwork()));

                    } else {
                        messageDAO.create(new AccountMessage()
                                .setAccount(accountPlan.getAccount())
                                .setNetwork(network.getUuid())
                                .setMessageType(AccountMessageType.request)
                                .setTarget(ActionTarget.network)
                                .setAction(AccountAction.payment)
                                .setName(accountPlan.getUuid())
                                .setData(accountPlan.getNetwork()));
                    }
                }
            }
        }
    }

    private List<Bill> billPlan(BubblePlan plan, AccountPlan accountPlan) {
        final Bill recentBill = billDAO.findMostRecentBillForAccountPlan(accountPlan.getUuid());
        if (recentBill == null) return die("billPlan: no recent bill found for accountPlan: "+accountPlan.getUuid());

        // start with any existing unpaid bills
        final List<Bill> bills = billDAO.findUnpaidByAccountPlan(accountPlan.getUuid());
        final BillPeriod period = plan.getPeriod();

        // create bills for the past, until a bill has a periodEnd beyond the AccountPlan.nextBill date
        Bill bill = recentBill;
        while (true) {
            final long nextBillMillis = period.periodMillis(bill.getPeriodEnd());
            final Bill nextBill = billDAO.newBill(plan, accountPlan, nextBillMillis);
            if (nextBillMillis <= now()) {
                bill = billDAO.create(nextBill);
                bills.add(bill);
            } else {
                accountPlan.setNextBill(nextBillMillis);
                accountPlan.setNextBillDate();
                accountPlanDAO.update(accountPlan);
                break;
            }
        }

        if (bills.size() > 1) {
            log.warn("billPlan: "+bills.size()+" bills found for accountPlan: "+accountPlan.getUuid());
        }
        return bills;
    }

    private boolean payBills(BubblePlan plan, AccountPlan accountPlan, List<Bill> bills) {
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());
        if (paymentMethod == null) return die("payBills: paymentMethod "+accountPlan.getPaymentMethod()+" not found for accountPlan: "+accountPlan.getUuid());

        final CloudService paymentService = cloudDAO.findByUuid(paymentMethod.getCloud());
        if (paymentService == null) return die("payBills: payment cloud "+paymentMethod.getCloud()+" not found for paymentMethod: "+paymentMethod.getUuid()+", accountPlan: "+accountPlan.getUuid());

        final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
        for (Bill bill : bills) {
            if (paymentDriver.getPaymentMethodType().requiresAuth()) {
                if (!paymentDriver.authorize(plan, accountPlan.getUuid(), bill.getUuid(), paymentMethod)) {
                    return die("payBills: paymentDriver.authorized returned false for accountPlan="+accountPlan.getUuid()+", paymentMethod="+paymentMethod.getUuid()+", bill="+bill.getUuid());
                }
            }
            if (!paymentDriver.purchase(accountPlan.getUuid(), paymentMethod.getUuid(), bill.getUuid())) {
                return die("payBills: paymentDriver.purchase returned false for accountPlan="+accountPlan.getUuid()+", paymentMethod="+paymentMethod.getUuid()+", bill="+bill.getUuid());
            }
        }
        return true;
    }

}
