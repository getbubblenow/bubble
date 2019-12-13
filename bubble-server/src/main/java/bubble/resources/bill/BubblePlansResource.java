package bubble.resources.bill;

import bubble.dao.bill.BubblePlanDAO;
import bubble.model.bill.BubblePlan;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.PLANS_ENDPOINT;

@Path(PLANS_ENDPOINT)
@Service @Slf4j
public class BubblePlansResource extends AccountOwnedResource<BubblePlan, BubblePlanDAO> {

    public BubblePlansResource() { super(null); }

}
