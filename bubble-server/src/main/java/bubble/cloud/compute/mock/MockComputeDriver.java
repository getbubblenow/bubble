package bubble.cloud.compute.mock;

import bubble.cloud.compute.ComputeServiceDriverBase;
import bubble.model.cloud.BubbleNode;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;

public class MockComputeDriver extends ComputeServiceDriverBase {

    private Map<String, BubbleNode> nodes = new ConcurrentHashMap<>();

    @Override protected String readSshKeyId(HttpResponseBean keyResponse) { return "dummy_ssh_key_id_"+now(); }

    @Override public String registerSshKey(BubbleNode node) { return readSshKeyId(null); }

    @Override protected HttpRequestBean registerSshKeyRequest(BubbleNode node) { return null; }

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        node.setIp4("127.0.0.1");
        node.setIp6("::1");
        return node;
    }

    @Override public List<BubbleNode> listNodes() throws IOException {
        return null;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception { return node; }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        return null;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        return null;
    }

}
