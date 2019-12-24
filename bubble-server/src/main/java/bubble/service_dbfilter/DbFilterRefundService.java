package bubble.service_dbfilter;

import bubble.service.bill.RefundService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterRefundService implements RefundService {

    @Override public void processRefunds() { notSupported("processRefunds"); }

}
