package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentsResource extends ReadOnlyAccountOwnedResource<AccountPayment, AccountPaymentDAO> {

    public AccountPaymentsResource(Account account) { super(account); }

}
