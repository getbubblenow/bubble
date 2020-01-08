package bubble.service.boot;

import bubble.cloud.CloudServiceType;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.cloud.compute.local.LocalComputeDriver;
import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.*;
import bubble.model.account.Account;
import bubble.model.boot.ActivationRequest;
import bubble.model.boot.CloudServiceConfig;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.ModelSetupService;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT_OBJECT;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.model.cloud.BubbleNetwork.TAG_PARENT_ACCOUNT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.toStringOrDie;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.getFirstPublicIpv4;
import static org.cobbzilla.util.network.NetworkUtil.getLocalhostIpv4;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class ActivationService {

    public static final String DEFAULT_ROLES = "ansible/default_roles.json";

    public static final long ACTIVATION_TIMEOUT = SECONDS.toMillis(10);

    @Autowired private AnsibleRoleDAO roleDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private StandardSelfNodeService selfNodeService;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private ModelSetupService modelSetupService;

    public BubbleNode bootstrapThisNode(Account account, ActivationRequest request) {
        String ip = getFirstPublicIpv4();
        if (ip == null) {
            log.warn("thisNode.ip4 will be localhost address, may not be reachable from other nodes");
            ip = getLocalhostIpv4();
        }
        if (ip == null) die("bootstrapThisNode: no IP could be found, not even a localhost address");

        final Map<String, CloudServiceConfig> requestConfigs = request.getCloudConfigs();
        final Map<String, CloudService> defaultConfigs = getCloudDefaultsMap();
        final ValidationResult errors = new ValidationResult();
        final List<CloudService> toCreate = new ArrayList<>();
        CloudService publicDns = null;
        CloudService localStorage = null;
        CloudService networkStorage = null;
        CloudService email = null;
        CloudService compute = null;
        for (Map.Entry<String, CloudServiceConfig> requestedCloud : requestConfigs.entrySet()) {
            final String name = requestedCloud.getKey();
            final CloudServiceConfig config = requestedCloud.getValue();
            final CloudService defaultCloud = defaultConfigs.get(name);
            if (defaultCloud == null) {
                errors.addViolation("err.cloud.notFound", "No cloud template found with name: "+name, name);

            } else if (errors.isValid()) {
                final CloudService cloud = new CloudService(defaultCloud).configure(config, errors);
                toCreate.add(cloud);
                switch (defaultCloud.getType()) {
                    case dns:
                        if (defaultCloud.getName().equals(request.getDomain().getPublicDns()) && publicDns == null) publicDns = cloud;
                        break;
                    case storage:
                        if (localStorage == null && defaultCloud.isLocalStorage()) localStorage = cloud;
                        if (networkStorage == null && defaultCloud.isNotLocalStorage()) networkStorage = cloud;
                        break;
                    case compute:
                        if (compute == null) compute = cloud;
                        break;
                    case email:
                        if (email == null) email = cloud;
                        break;
                }
            }
        }
        if (publicDns == null) errors.addViolation("err.publicDns.noneSpecified");
        if (networkStorage == null) errors.addViolation("err.storage.noneSpecified");
        if (compute == null && !configuration.testMode()) errors.addViolation("err.compute.noneSpecified");
        if (email == null && !configuration.testMode()) errors.addViolation("err.email.noneSpecified");
        if (errors.isInvalid()) throw invalidEx(errors);

        // create local storage if it was not provided
        if (localStorage == null) {
            localStorage = cloudDAO.create(new CloudService()
                    .setAccount(account.getUuid())
                    .setType(CloudServiceType.storage)
                    .setDriverClass(LocalStorageDriver.class.getName())
                    .setDriverConfigJson(json(new LocalStorageConfig().setBaseDir(configuration.getLocalStorageDir())))
                    .setName(LOCAL_STORAGE)
                    .setTemplate(true));
        }

        // create clouds, test cloud drivers
        for (CloudService cloud : toCreate) {
            final Object testArg;
            if (cloud == publicDns) {
                testArg = request.getDomain().getName();
            } else {
                testArg = null;
            }
            cloudDAO.create(cloud
                    .setTemplate(true)
                    .setEnabled(true)
                    .setAccount(account.getUuid())
                    .setTestArg(testArg));
        }
        if (errors.isInvalid()) throw invalidEx(errors);

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

        if (request.createDefaultObjects()) {
            background(() -> {
                // wait for activation to complete
                final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
                final long start = now();
                while (!accountDAO.activated() && now() - start < ACTIVATION_TIMEOUT) {
                    sleep(SECONDS.toMillis(1), "waiting for activation to complete before creating default objects");
                }
                if (!accountDAO.activated()) die("bootstrapThisNode: timeout waiting for activation to complete, default objects not created");

                final ApiClientBase api = configuration.newApiClient().setToken(account.getToken());
                final Map<CrudOperation, Collection<Identifiable>> objects
                        = modelSetupService.setupModel(api, account, "manifest-dist");
                log.info("bootstrapThisNode: created default objects\n"+json(objects));
            });
        }

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
        network.setUuid(ROOT_NETWORK_UUID);
        return networkDAO.create(network);
    }

    @Getter(lazy=true) private final CloudService[] cloudDefaults = initCloudDefaults();
    private CloudService[] initCloudDefaults() {
        final CloudService[] standardServices = loadCloudServices("cloudService");
        return configuration.paymentsEnabled()
                ? ArrayUtil.concat(standardServices, loadCloudServices("cloudService_payment"))
                : standardServices;
    }

    private CloudService[] loadCloudServices(final String services) {
        return json(HandlebarsUtil.apply(configuration.getHandlebars(), stream2string("models/defaults/" + services + ".json"), configuration.getEnvCtx()), CloudService[].class);
    }

    @Getter(lazy=true) private final Map<String, CloudService> cloudDefaultsMap = initCloudDefaultsMap();
    private Map<String, CloudService> initCloudDefaultsMap() {
        return Arrays.stream(getCloudDefaults()).collect(toMap(CloudService::getName, identity()));
    }
}
