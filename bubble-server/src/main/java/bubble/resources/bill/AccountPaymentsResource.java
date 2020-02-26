/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentDAO;
import bubble.dao.bill.BillDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

import java.util.List;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentsResource extends ReadOnlyAccountOwnedResource<AccountPayment, AccountPaymentDAO> {

    @Autowired private BillDAO billDAO;

    private Bill bill;
    private AccountPlan accountPlan;

    public AccountPaymentsResource(Account account) { super(account); }

    public AccountPaymentsResource(Account account, Bill bill) {
        this(account);
        this.bill = bill;
    }

    public AccountPaymentsResource(Account account, AccountPlan accountPlan) {
        this(account);
        this.accountPlan = accountPlan;
    }

    @Override protected AccountPayment find(ContainerRequest ctx, String id) {
        final AccountPayment payment = super.find(ctx, id);
        if (bill != null && !payment.getBill().equals(bill.getUuid())) return null;
        if (accountPlan != null && !payment.getAccountPlan().equals(accountPlan.getUuid())) return null;
        return payment;
    }

    @Override protected List<AccountPayment> list(ContainerRequest ctx) {
        if (bill == null) {
            if (accountPlan == null) {
                return super.list(ctx);
            } else {
                return getDao().findByAccountAndAccountPlan(getAccountUuid(ctx), accountPlan.getUuid());
            }
        } else if (accountPlan != null) {
            return getDao().findByAccountAndAccountPlanAndBill(getAccountUuid(ctx), accountPlan.getUuid(), bill.getUuid());

        } else {
            return getDao().findByAccountAndBill(getAccountUuid(ctx), bill.getUuid());
        }
    }

    @Override protected AccountPayment populate(ContainerRequest ctx, AccountPayment payment) {
        return super.populate(ctx, payment)
                .setBillObject(bill != null ? bill : billDAO.findByUuid(payment.getBill()));
    }
}
