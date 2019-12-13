package bubble.server;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServerBase;

import static org.cobbzilla.util.network.NetworkUtil.IPv4_LOCALHOST;

@Slf4j
public class BubbleDbFilterServer extends RestServerBase<BubbleConfiguration> {

    @Override protected String getListenAddress() { return IPv4_LOCALHOST; }

}
