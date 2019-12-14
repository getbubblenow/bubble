package bubble.service.bill;

import bubble.dao.bill.AccountPaymentMethodDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RefundService {

    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;

    public void processRefunds () {
        // todo: wake up background job to look for AccountPlans that have been deleted but not refunded
    }

}
