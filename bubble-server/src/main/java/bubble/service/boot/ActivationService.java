package bubble.service.boot;

import bubble.ApiConstants;
import bubble.cloud.CloudServiceType;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.cloud.compute.local.LocalComputeDriver;
import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.cloud.*;
import bubble.model.account.Account;
import bubble.model.account.ActivationRequest;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;

import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT_OBJECT;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.model.cloud.BubbleNetwork.TAG_PARENT_ACCOUNT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.toStringOrDie;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.getFirstPublicIpv4;
import static org.cobbzilla.util.network.NetworkUtil.getLocalhostIpv4;
import static org.cobbzilla.util.system.CommandShell.execScript;

@Service @Slf4j
public class ActivationService {

    public static final String DEFAULT_ROLES = "ansible/default_roles.json";

    public static final long ROOT_CREATE_TIMEOUT = SECONDS.toMillis(10);

    @Autowired private AnsibleRoleDAO roleDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private StandardSelfNodeService selfNodeService;
    @Autowired private BubbleConfiguration configuration;

    public BubbleNode bootstrapThisNode(Account account, ActivationRequest request) {
        String ip = getFirstPublicIpv4();
        if (ip == null) {
            log.warn("thisNode.ip4 will be localhost address, may not be reachable from other nodes");
            ip = getLocalhostIpv4();
        }
        if (ip == null) die("bootstrapThisNode: no IP could be found, not even a localhost address");

        final CloudService publicDns = cloudDAO.create(new CloudService(request.getDns())
                .setType(CloudServiceType.dns)
                .setTemplate(true)
                .setAccount(account.getUuid()));

        final CloudService localStorage = cloudDAO.create(new CloudService()
                .setAccount(account.getUuid())
                .setType(CloudServiceType.storage)
                .setDriverClass(LocalStorageDriver.class.getName())
                .setDriverConfigJson(json(new LocalStorageConfig().setBaseDir(configuration.getLocalStorageDir())))
                .setName(LOCAL_STORAGE)
                .setTemplate(true));

        final CloudService networkStorage;
        if (request.getStorage().getName().equals(LOCAL_STORAGE)) {
            networkStorage = localStorage;
        } else {
            networkStorage = cloudDAO.create(new CloudService(request.getStorage())
                    .setAccount(account.getUuid()));
        }

        final AnsibleRole[] roles = request.hasRoles() ? request.getRoles() : json(loadDefaultRoles(), AnsibleRole[].class);
        for (AnsibleRole role : roles) {
            roleDAO.create(role.setAccount(account.getUuid()));
        }

        final BubbleDomain domain = domainDAO.create(new BubbleDomain(request.getDomain())
                .setAccount(account.getUuid())
                .setPublicDns(publicDns.getUuid())
                .setRoles(Arrays.stream(roles).map(AnsibleRole::getName).toArray(String[]::new))
                .setTemplate(true));

        BubbleFootprint footprint = footprintDAO.findByAccountAndId(account.getUuid(), DEFAULT_FOOTPRINT);
        if (footprint == null) {
            footprint = footprintDAO.create(DEFAULT_FOOTPRINT_OBJECT.setAccount(account.getUuid()));
        }

        final BubbleNetwork network = createRootNetwork(new BubbleNetwork()
                .setAccount(account.getUuid())
                .setFootprint(footprint.getUuid())
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setComputeSizeType(ComputeNodeSizeType.local)
                .setName(request.getNetworkName())
                .setTag(TAG_ALLOW_REGISTRATION, true)
                .setTag(TAG_PARENT_ACCOUNT, account.getUuid())
                .setStorage(networkStorage.getUuid())
                .setState(BubbleNetworkState.running));

        // copy data outside the network to inside the network
        final LocalStorageDriver storageDriver = (LocalStorageDriver) localStorage.getStorageDriver(configuration);
        storageDriver.migrateInitialData(network);

        final CloudService localCloud = cloudDAO.create(new CloudService()
                .setAccount(account.getUuid())
                .setType(CloudServiceType.local)
                .setDriverClass(LocalComputeDriver.class.getName())
                .setDriverConfigJson("{}")
                .setName(request.getNetworkName()+"_compute")
                .setTemplate(false));

        final BubbleNode node = nodeDAO.create(new BubbleNode()
                .setAccount(account.getUuid())
                .setNetwork(network.getUuid())
                .setDomain(network.getDomain())
                .setSize("local")
                .setRegion("local")
                .setSizeType(ComputeNodeSizeType.local)
                .setCloud(localCloud.getUuid())
                .setIp4(ip)
                // todo: also set ip6 if we have one
                .setAdminPort(configuration.getHttp().getPort())
                .setState(BubbleNodeState.running));

        final BubbleNodeKey key = nodeKeyDAO.create(new BubbleNodeKey(node));

        selfNodeService.setActivated(node);

        String[] domainRoles = request.getDomain().getRoles();
        if (domainRoles == null || domainRoles.length == 0) {
            domainRoles = Arrays.stream(roles).map(AnsibleRole::getName).toArray(String[]::new);
        }
        domainDAO.update(domain.setRoles(domainRoles));

        selfNodeService.initThisNode(node);
        configuration.refreshPublicSystemConfigs();

        return node;
    }

    public String loadDefaultRoles() {
        if (configuration.testMode()) {
            final File roleFile = new File("target/classes/"+DEFAULT_ROLES);
            final String rolesJson = toStringOrDie(roleFile);
            if (rolesJson == null || !rolesJson.contains(STORAGE_PREFIX)) execScript("../bin/prep_bubble_jar");
            return toStringOrDie(roleFile);
        } else {
            return stream2string(DEFAULT_ROLES);
        }
    }

    public BubbleNetwork createRootNetwork(BubbleNetwork network) {
        network.setUuid(ApiConstants.ROOT_NETWORK_UUID);
        return networkDAO.create(network);
    }

}
