package bubble.mock;

import bubble.service.cloud.StandardNetworkService;
import org.cobbzilla.util.system.CommandResult;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class MockNetworkService extends StandardNetworkService {

    @Override public CommandResult ansibleSetup(String script) throws IOException {
        return new CommandResult(0, "mock: successful", "");
    }

}
