package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPaymentMethod;
import bubble.resources.account.AccountOwnedResource;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentMethodsResource extends AccountOwnedResource<AccountPaymentMethod, AccountPaymentMethodDAO> {

    @Autowired private BubbleConfiguration configuration;

    public AccountPaymentMethodsResource(Account account) { super(account); }

    @Override protected AccountPaymentMethod find(ContainerRequest ctx, String id) {
        final AccountPaymentMethod found = super.find(ctx, id);
        return found == null || found.deleted() ? null : found;
    }

    @Override protected List<AccountPaymentMethod> list(ContainerRequest ctx) {
        return super.list(ctx).stream().filter(AccountPaymentMethod::notDeleted).collect(Collectors.toList());
    }

    @Override protected AccountPaymentMethod setReferences(ContainerRequest ctx, Account caller, AccountPaymentMethod request) {
        final ValidationResult result = new ValidationResult();
        request.validate(result, configuration);
        if (result.isInvalid()) throw invalidEx(result);
        return super.setReferences(ctx, caller, request);
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountPaymentMethod found, AccountPaymentMethod request) {
        return false;
    }

}
