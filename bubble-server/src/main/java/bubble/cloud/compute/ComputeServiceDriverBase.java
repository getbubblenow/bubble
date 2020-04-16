/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudServiceDriverBase;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.model.cloud.BubbleNode.TAG_SSH_KEY_ID;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.security.RsaKeyPair.newRsaKeyPair;

@Slf4j
public abstract class ComputeServiceDriverBase
        extends CloudServiceDriverBase<ComputeConfig>
        implements ComputeServiceDriver {

    private final AtomicReference<NodeReaper> reaper = new AtomicReference<>();

    @Override public void postSetup() {
        if (configuration.isSelfSage()) {
            synchronized (reaper) {
                if (reaper.get() == null) {
                    reaper.set(new NodeReaper(this));
                    configuration.autowire(reaper.get()).start();
                }
            }
        } else {
            log.info("startDriver("+getClass().getSimpleName()+"): not self-sage, not starting NodeReaper");
        }
    }

    @Autowired protected BubbleNodeDAO nodeDAO;

    public String registerSshKey(BubbleNode node) {
        if (node.hasSshKey()) return die("registerSshKey: node already has a key: "+node.getUuid());
        node.setSshKey(newRsaKeyPair());
        final HttpRequestBean keyRequest = registerSshKeyRequest(node);
        final HttpResponseBean keyResponse = keyRequest.curl();  // fixme: we can do better than shelling to curl
        if (keyResponse.getStatus() != 200) return die("start: error creating SSH key: " + keyResponse);

        final String keyId = readSshKeyId(keyResponse);

        node.setTag(TAG_SSH_KEY_ID, keyId);
        nodeDAO.update(node);
        return keyId;
    }

    protected abstract String readSshKeyId(HttpResponseBean keyResponse);

    protected abstract HttpRequestBean registerSshKeyRequest(BubbleNode node);

    public abstract List<BubbleNode> listNodes() throws IOException;

    @Override public List<CloudRegion> getRegions() { return Arrays.asList(config.getRegions()); }
    @Override public List<ComputeNodeSize> getSizes() { return Arrays.asList(config.getSizes()); }

    @Override public CloudRegion getRegion(String region) {
        return getRegions().stream()
                .filter(r -> r.getName().equalsIgnoreCase(region) || r.getInternalName().equalsIgnoreCase(region))
                .findAny().orElse(null);
    }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) {
        return getSizes().stream()
                .filter(s -> s.getType() == type)
                .findAny().orElse(null);
    }

}
