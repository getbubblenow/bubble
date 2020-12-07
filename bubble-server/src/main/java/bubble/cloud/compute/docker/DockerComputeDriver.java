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
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.service.packer.PackerJob.PACKER_IMAGE_PREFIX;
import static com.github.dockerjava.api.model.InternetProtocol.UDP;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.util.io.StreamUtil.stream2file;
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

    public static final ExposedPort[] SAGE_EXPOSED_PORTS = {
            new ExposedPort(22), new ExposedPort(80), new ExposedPort(443), new ExposedPort(1202)
    };
    public static final ExposedPort[] NODE_EXPOSED_PORTS = ArrayUtil.append(SAGE_EXPOSED_PORTS,
            new ExposedPort(1080), new ExposedPort(1443),
            new ExposedPort(8888), new ExposedPort(9999),
            new ExposedPort(53, UDP),
            new ExposedPort(500, UDP), new ExposedPort(4500, UDP),
            new ExposedPort(51820, UDP)
    );

    @Override public Map<String, ComputeNodeSize> getSizesMap() { return NODE_SIZE_MAP; }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) { return LOCAL_SIZE; }

    public static final String BASE_IMAGE = "phusion/baseimage:focal-1.0.0alpha1-amd64";

    public static final List<OsImage> CLOUD_OS_IMAGES = Arrays.asList(new OsImage[]{
            new OsImage().setName(BASE_IMAGE).setId(BASE_IMAGE).setRegion(LOCAL)
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

    private static final String[] PACKER_FILES = {
            "run_redis.sh", "run_postgresql.sh", "run_supervisor.sh", "run_cron.sh", "run_nginx.sh"
    };

    @Override public void prepPackerDir(TempDir tempDir) {
        try {
            for (String p : PACKER_FILES) {
                final File destFile = new File(abs(tempDir) + "/roles/common/files/" + p);
                if (!destFile.getParentFile().exists()) die("prepPackerDir: parent dir does not exist: "+abs(destFile.getParentFile()));
                stream2file(loadResourceAsStream("docker/" + p), destFile);
            }
        } catch (Exception e) {
            die("prepPackerDir: "+shortError(e), e);
        }
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception { return node; }

    @Override public BubbleNode start(BubbleNode node) throws Exception {

        final AnsibleInstallType installType = node.getInstallType();
        if (!supportsPacker(installType)) {
            return die("start("+node.id()+"): packer for "+installType+" not supported on "+CURRENT_OS);
        }

        final DockerClient dc = getDockerClient();

        final PackerImage packerImage = getOrCreatePackerImage(node);

        final CreateContainerCmd ccr = dc.createContainerCmd(packerImage.getId())
                .withExposedPorts(node.getInstallType() == AnsibleInstallType.sage ? SAGE_EXPOSED_PORTS : NODE_EXPOSED_PORTS)
                .withLabels(MapBuilder.build(new String[][] {
                        {LABEL_CLOUD, cloud.getUuid()},
                        {LABEL_NODE, node.getUuid()}
                }))
                .withHostConfig(HostConfig.newHostConfig()
                        .withCapAdd(Capability.NET_ADMIN)
                        .withCapAdd(Capability.SYS_MODULE)
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
        if (containerId == null) return die("start("+node.id()+"): not not found after starting");
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
        if (empty(containers)) {
            log.warn("lookupContainer: node not found: " + node.getUuid());
            return null;
        }
        if (containers.size() > 1) {
            log.warn("lookupContainer: multiple containers found for node: " + node.getUuid());
            return null;
        }
        return containers.get(0).getId();
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        final DockerClient dc = getDockerClient();
        final String containerId = lookupContainer(node);
        if (containerId == null) {
            log.warn("stop("+node.id()+") node not found");
            return node;
        }
        dc.stopContainerCmd(containerId).exec();
        return node;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final DockerClient dc = getDockerClient();
        final String containerId = lookupContainer(node);
        if (containerId == null) {
            log.warn("status("+node.id()+"): node not found, returning 'stopped'");
            return node.setState(BubbleNodeState.stopped);
        }
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
