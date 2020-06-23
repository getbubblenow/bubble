package bubble.service_dbfilter;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.service.stream.AppPrimerService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterAppPrimerService implements AppPrimerService {

    @Override public void primeApps() { notSupported("primeApps"); }

    @Override public void prime(Account account, String app) { notSupported("prime"); }

    @Override public void prime(BubbleApp app) { notSupported("prime"); }

}
