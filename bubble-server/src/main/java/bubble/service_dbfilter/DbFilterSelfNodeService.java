package bubble.service_dbfilter;

import bubble.model.cloud.BubbleNetwork;
import bubble.service.boot.SelfNodeService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterSelfNodeService implements SelfNodeService {

    @Override public BubbleNetwork getThisNetwork() { return notSupported("getThisNetwork"); }

    @Override public void refreshThisNetwork() { notSupported("refreshThisNetwork"); }

}
