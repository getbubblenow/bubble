/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.cloud.CloudServiceType;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.cloud.compute.local.LocalComputeDriver;
import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountSshKeyDAO;
import bubble.dao.cloud.*;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.boot.ActivationRequest;
import bubble.model.boot.CloudServiceConfig;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.ModelSetupService;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT;
import static bubble.model.cloud.BubbleFootprint.DEFAULT_FOOTPRINT_OBJECT;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.model.cloud.BubbleNetwork.TAG_PARENT_ACCOUNT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.getFirstPublicIpv4;
import static org.cobbzilla.util.network.NetworkUtil.getLocalhostIpv4;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.scrubSpecial;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class ActivationService {

    public static final long ACTIVATION_TIMEOUT = SECONDS.toMillis(10);

    @Autowired private AccountSshKeyDAO sshKeyDAO;
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

        if (request.hasSshKey()) {
            final String keyJson = json(request.getSshKey());
            if (keyJson.contains("{{") && keyJson.contains("}}")) {
                request.setSshKey(json(configuration.applyHandlebars(keyJson), AccountSshKey.class));
            }
            sshKeyDAO.create(request.getSshKey().setAccount(account.getUuid()));
        }

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
        final boolean testMode = configuration.testMode();
        if (publicDns == null) errors.addViolation("err.publicDns.noneSpecified");
        if (networkStorage == null && !testMode) errors.addViolation("err.storage.noneSpecified");
        if (compute == null && !testMode) errors.addViolation("err.compute.noneSpecified");
        if (email == null && !testMode) errors.addViolation("err.email.noneSpecified");
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
                    .setTestArg(testArg)
                    .setSkipTest(request.skipTests()));
        }
        if (errors.isInvalid()) throw invalidEx(errors);

        final BubbleDomain domain = domainDAO.create(new BubbleDomain(request.getDomain())
                .setAccount(account.getUuid())
                .setPublicDns(publicDns.getUuid())
                .setTemplate(true));

        BubbleFootprint footprint = footprintDAO.findByAccountAndId(account.getUuid(), DEFAULT_FOOTPRINT);
        if (footprint == null) {
            footprint = footprintDAO.create(new BubbleFootprint(DEFAULT_FOOTPRINT_OBJECT).setAccount(account.getUuid()));
        }

        final BubbleNetwork network = createRootNetwork(new BubbleNetwork()
                .setAccount(account.getUuid())
                .setFootprint(footprint.getUuid())
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setComputeSizeType(ComputeNodeSizeType.local)
                .setInstallType(AnsibleInstallType.sage)
                .setLaunchType(LaunchType.fork_sage)
                .setName(request.getNetworkName())
                .setTag(TAG_ALLOW_REGISTRATION, true)
                .setTag(TAG_PARENT_ACCOUNT, account.getUuid())
                .setStorage(networkStorage != null ? networkStorage.getUuid() : localStorage.getUuid())
                .setState(BubbleNetworkState.running)
                .setSyncAccount(false)
                .setLaunchLock(false)
                .setSendErrors(false)
                .setSendMetrics(false));
        selfNodeService.refreshThisNetwork();

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
                .setInstallType(AnsibleInstallType.sage)
                .setSize("local")
                .setRegion("local")
                .setSizeType(ComputeNodeSizeType.local)
                .setCloud(localCloud.getUuid())
                .setSslPort(network.getSslPort())
                .setIp4(ip)
                // todo: also set ip6 if we have one
                .setAdminPort(configuration.getHttp().getPort())
                .setState(BubbleNodeState.running));

        final BubbleNodeKey key = nodeKeyDAO.create(new BubbleNodeKey(node));

        selfNodeService.setActivated(node);
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

                @Cleanup final ApiClientBase api = configuration.newApiClient().setToken(account.getToken());
                final Map<CrudOperation, Collection<Identifiable>> objects
                        = modelSetupService.setupModel(api, account, "manifest-defaults");
                log.info("bootstrapThisNode: created default objects\n"+json(objects));
            }, "ActivationService.bootstrapThisNode.createDefaultObjects");
        }

        return node;
    }

    public BubbleNetwork createRootNetwork(BubbleNetwork network) {
        network.setUuid(ROOT_NETWORK_UUID);
        return networkDAO.create(network);
    }

    @Getter(lazy=true) private final CloudService[] cloudDefaults = initCloudDefaults();
    private CloudService[] initCloudDefaults() {
        CloudService[] defaults = new CloudService[0];
        for (String modelPath : configuration.getDefaultCloudModels()) {
            defaults = ArrayUtil.concat(defaults, loadCloudServices(modelPath));
        }
        return defaults;
    }

    private CloudService[] loadCloudServices(final String modelPath) {
        final String cloudsJson = configuration.applyHandlebars(stream2string(modelPath));
        final JsonNode cloudsArrayNode = json(cloudsJson, JsonNode.class);
        return scrubSpecial(cloudsArrayNode, CloudService.class);
    }

    @Getter(lazy=true) private final Map<String, CloudService> cloudDefaultsMap = initCloudDefaultsMap();
    private Map<String, CloudService> initCloudDefaultsMap() {
        final Map<String, CloudService> defaults = new HashMap<>();
        Arrays.stream(getCloudDefaults()).forEach(c -> defaults.put(c.getName(), c));
        return defaults;
    }
}
