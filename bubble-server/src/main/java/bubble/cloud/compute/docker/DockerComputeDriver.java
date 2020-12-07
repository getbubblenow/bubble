package bubble.cloud.compute.docker;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.*;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import bubble.model.cloud.CloudCredentials;
import bubble.service.packer.PackerBuild;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.MapBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.service.packer.PackerJob.PACKER_IMAGE_PREFIX;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.OsType.CURRENT_OS;
import static org.cobbzilla.util.system.OsType.linux;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class DockerComputeDriver extends ComputeServiceDriverBase {

    public static final String LOCAL = "local";

    public static final CloudRegion[] CLOUD_REGIONS_ARRAY = new CloudRegion[]{
            new CloudRegion().setName(LOCAL).setInternalName(LOCAL).setLocation(GeoLocation.NULL_LOCATION)
    };
    public static final List<CloudRegion> CLOUD_REGIONS = Arrays.asList(CLOUD_REGIONS_ARRAY);

    public static final ComputeNodeSize LOCAL_SIZE = new ComputeNodeSize().setName(LOCAL).setInternalName(LOCAL).setType(ComputeNodeSizeType.local);
    public static final List<ComputeNodeSize> CLOUD_SIZES = singletonList(LOCAL_SIZE);
    public static final Map<String, ComputeNodeSize> NODE_SIZE_MAP = MapBuilder.build(LOCAL, LOCAL_SIZE);

    @Override public Map<String, ComputeNodeSize> getSizesMap() { return NODE_SIZE_MAP; }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) { return LOCAL_SIZE; }

    public static final List<OsImage> CLOUD_OS_IMAGES = Arrays.asList(new OsImage[]{
            new OsImage().setName("ubuntu:20.04").setId("ubuntu:20.04").setRegion(LOCAL)
    });

    public static final long START_TIMEOUT = SECONDS.toMillis(120);
    public static final String DEFAULT_HOST = "unix:///var/run/docker.sock";

    private static final String LABEL_IMAGE = "bubble_image";
    private static final String LABEL_CLOUD = "bubble_cloud";
    private static final String LABEL_NODE = "bubble_node";

    @Getter private final List<CloudRegion> cloudRegions = CLOUD_REGIONS;
    @Getter private final List<ComputeNodeSize> cloudSizes = CLOUD_SIZES;
    @Getter private final List<OsImage> cloudOsImages = CLOUD_OS_IMAGES;

    @Override public boolean supportsPacker(AnsibleInstallType installType) {
        boolean supported = installType == AnsibleInstallType.sage || CURRENT_OS == linux;
        if (!supported) log.warn("supportsPacker: installType "+installType+" not supported (no images will be created) for platform: "+CURRENT_OS);
        return supported;
    }

    @Override public boolean supportsDns() { return false; }

    @Override public CloudRegion[] getRegions(PackerBuild packerBuild) { return CLOUD_REGIONS_ARRAY; }

    @Override public String getPackerImageId(String name, PackerBuild packerBuild) { return name; }

    private final Map<String, Map<Integer, Integer>> portMappings = new ConcurrentHashMap();

    @Override public int getSshPort(BubbleNode node) {
        return portMappings.get(node.getUuid()).get(1202);
    }

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

        final CreateContainerCmd ccr = dc.createContainerCmd(packerImage.getId())
                .withLabels(MapBuilder.build(new String[][] {
                        {LABEL_CLOUD, cloud.getUuid()},
                        {LABEL_NODE, node.getUuid()}
                }))
                .withHostConfig(HostConfig.newHostConfig()
                        .withCapAdd(Capability.NET_ADMIN)
                        .withCapAdd(Capability.SYS_ADMIN));
        final CreateContainerResponse response = ccr.exec();
        final long start = now();
        final Predicate<? super BubbleNode> nodeFilter = filterForNode(node);
        while (listNodes().stream().noneMatch(nodeFilter)) {
            if (now() - start > START_TIMEOUT) {
                return die("start("+node.id()+"): timeout");
            }
            sleep(SECONDS.toMillis(5), "waiting for docker container to be running");
        }
        final String containerId = lookupContainer(node);
        final InspectContainerResponse status = dc.inspectContainerCmd(containerId).exec();

        return node.setIp4("127.0.0.1").setIp6("fd00::1");
    }

    private Predicate<? super BubbleNode> filterForNode(BubbleNode node) {
        return n -> n.isRunning() && n.getUuid().equals(node.getUuid());
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
        final String repository;
        try {
            repository = getConfig().getPacker().getPost().get("repository").asText();
            if (empty(repository)) die("repository value was empty");
        } catch (Exception e) {
            log.error("getAllPackerImages: no repository found in packer.post config (returning empty list): "+shortError(e));
            return emptyList();
        }
        final String prefix = repository + ":" + PACKER_IMAGE_PREFIX;
        final List<Image> images = dc.listImagesCmd().exec().stream()
                .filter(i -> i.getRepoTags() != null && Arrays.stream(i.getRepoTags()).anyMatch(t -> t.startsWith(prefix)))
                .collect(Collectors.toList());
        final List<PackerImage> packerImages = new ArrayList<>();
        for (Image i : images) {
            final PackerImage p = new PackerImage();
            final String name;
            if (empty(i.getRepoTags())) {
                name = i.getId();
            } else if (i.getRepoTags().length == 1) {
                final String repoTag = i.getRepoTags()[0];
                if (repoTag.contains(":")) {
                    name = repoTag.substring(repoTag.indexOf(":") + 1);
                } else {
                    name = repoTag;
                }
            } else {
                name = json(i.getRepoTags());
            }
            p.setId(i.getId());
            p.setName(name);
            p.setRegions(CLOUD_REGIONS_ARRAY);
            packerImages.add(p);
        }
        return packerImages;
    }

    @Override public List<PackerImage> getPackerImagesForRegion(String region) {
        return region == null || region.equals(LOCAL) ? getAllPackerImages() : emptyList();
    }

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
