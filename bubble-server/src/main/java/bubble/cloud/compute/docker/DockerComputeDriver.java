package bubble.cloud.compute.docker;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.*;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import bubble.model.cloud.CloudCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import edu.emory.mathcs.backport.java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.MapBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static bubble.service.packer.PackerJob.PACKER_IMAGE_PREFIX;
import static java.lang.Boolean.parseBoolean;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class DockerComputeDriver extends ComputeServiceDriverBase {

    public static final List<CloudRegion> CLOUD_REGIONS = Arrays.asList(new CloudRegion[]{
            new CloudRegion().setName("local").setInternalName("local")
    });
    public static final List<ComputeNodeSize> CLOUD_SIZES = Arrays.asList(new ComputeNodeSize[]{
            new ComputeNodeSize().setName("local").setInternalName("local").setType(ComputeNodeSizeType.local)
    });
    public static final List<OsImage> CLOUD_OS_IMAGES = Arrays.asList(new OsImage[]{
            new OsImage().setName("ubuntu:20.04").setId("ubuntu:20.04").setRegion("local")
    });

    public static final long START_TIMEOUT = SECONDS.toMillis(120);
    public static final String DEFAULT_HOST = "unix:///var/run/docker.sock";

    private static final String LABEL_IMAGE = "bubble_image";
    private static final String LABEL_CLOUD = "bubble_cloud";
    private static final String LABEL_NODE = "bubble_node";

    @Getter private final List<CloudRegion> cloudRegions = CLOUD_REGIONS;
    @Getter private final List<ComputeNodeSize> cloudSizes = CLOUD_SIZES;
    @Getter private final List<OsImage> cloudOsImages = CLOUD_OS_IMAGES;

    @Getter(lazy=true) private final DockerClient dockerClient = initDockerClient();
    private DockerClient initDockerClient() {
        CloudCredentials creds = getCredentials();
        if (creds == null) creds = new CloudCredentials();

        final String host = creds.hasParam("host") ? creds.getParam("host") : DEFAULT_HOST;
        final boolean tlsVerify = creds.hasParam("tlsVerify") && parseBoolean(creds.getParam("tlsVerify"));
        final String certPath = creds.hasParam("certPath") ? creds.getParam("certPath") : null;

        final DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .withDockerTlsVerify(tlsVerify)
                .withDockerCertPath(certPath)
                .withRegistryUsername(creds.getParam("registryUsername"))
                .withRegistryPassword(creds.getParam("registryPassword"))
                .withRegistryEmail(creds.getParam("registryEmail"))
                .withRegistryUrl(creds.getParam("registryUrl"))
                .build();

        final DockerHttpClient client = new ZerodepDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .build();

        return DockerClientImpl.getInstance(dockerConfig, client);
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception { return node; }

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        final DockerClient dc = getDockerClient();

        final PackerImage packerImage = getOrCreatePackerImage(node);

        final CreateContainerResponse ccr = dc.createContainerCmd(packerImage.getId())
                .withLabels(MapBuilder.build(new String[][] {
                        {LABEL_CLOUD, cloud.getUuid()},
                        {LABEL_NODE, node.getUuid()}
                }))
                .exec();
        final long start = now();
        while (listNodes().stream().noneMatch(n -> n.isRunning() && n.getUuid().equals(node.getUuid()))) {
            if (now() - start > START_TIMEOUT) {
                return die("start("+node.id()+"): timeout");
            }
            sleep(SECONDS.toMillis(5), "waiting for docker container to be running");
        }
        return node;
    }

    private String lookupContainer(BubbleNode node) {
        final DockerClient dc = getDockerClient();
        final List<Container> containers = dc.listContainersCmd()
                .withLabelFilter(MapBuilder.build(LABEL_NODE, node.getUuid()))
                .exec();
        if (empty(containers)) return die("lookupContainer: node not found: "+node.getUuid());
        if (containers.size() > 1) return die("lookupContainer: multiple containers found for node: "+node.getUuid());
        return containers.get(0).getId();
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        final DockerClient dc = getDockerClient();
        final String containerId = lookupContainer(node);
        dc.stopContainerCmd(containerId).exec();
        return node;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final DockerClient dc = getDockerClient();
        final String containerId = lookupContainer(node);
        final InspectContainerResponse status = dc.inspectContainerCmd(containerId).exec();
        log.info("status("+node.id()+"): "+json(status));

        final Boolean dead = status.getState().getDead();
        if (dead != null && dead) return node.setState(BubbleNodeState.stopped);

        final Boolean running = status.getState().getRunning();
        if (running != null && running) return node.setState(BubbleNodeState.running);

        log.warn("status("+node.id()+"): recognized state: "+json(status.getState()));

        return node;
    }

    @Override public List<PackerImage> getAllPackerImages() {
        final DockerClient dc = getDockerClient();
        final List<Image> images = dc.listImagesCmd().withImageNameFilter(PACKER_IMAGE_PREFIX).withLabelFilter(MapBuilder.build(LABEL_IMAGE, PACKER_IMAGE_PREFIX)).exec();
        final List<PackerImage> packerImages = new ArrayList<>();
        for (Image i : images) {
            final PackerImage p = new PackerImage();
            p.setId(i.getId());
            p.setName(empty(i.getLabels()) ? i.getId() : i.getLabels().size() == 1 ? i.getLabels().values().iterator().next() : json(i.getLabels()));
            p.setRegions(null);
            packerImages.add(p);
        }
        return packerImages;
    }

    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return getAllPackerImages(); }

    @Override public List<BubbleNode> listNodes() throws IOException {
        final DockerClient dc = getDockerClient();
        final List<BubbleNode> nodes = new ArrayList<>();
        final List<Container> containers = dc.listContainersCmd()
                .withLabelFilter(MapBuilder.build(LABEL_CLOUD, cloud.getUuid()))
                .exec();
        for (Container c : containers) {
            final BubbleNode n = new BubbleNode().setState(BubbleNodeState.running);
            n.setUuid(c.getLabels().get(LABEL_NODE));
            n.setCloud(c.getLabels().get(LABEL_CLOUD));
            nodes.add(n);
        }
        return nodes;
    }

}
