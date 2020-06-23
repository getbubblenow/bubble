package bubble.service.stream;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;

public interface AppPrimerService {

    void primeApps();

    void prime(BubbleApp app);

    void prime(Account account, String app);

}
