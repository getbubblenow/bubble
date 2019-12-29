package bubble.service.cloud;

import bubble.cloud.CloudAndRegion;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.dns.DnsServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.*;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.cloud.notify.NotificationType;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.RestoreService;
import bubble.service.notify.NotificationService;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.validation.MultiViolationException;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.getRemoteHost;
import static bubble.ApiConstants.newNodeHostname;
import static bubble.model.cloud.BubbleNode.TAG_ERROR;
import static bubble.service.boot.StandardSelfNodeService.*;
import static bubble.service.cloud.NodeProgressMeter.getProgressMeterKey;
import static bubble.service.cloud.NodeProgressMeter.getProgressMeterPrefix;
import static bubble.service.cloud.NodeProgressMeterConstants.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.daemon;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.closeQuietly;
import static org.cobbzilla.util.system.CommandShell.chmod;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class StandardNetworkService implements NetworkService {

    public static final String ANSIBLE_DIR = "ansible";

    public static final String PLAYBOOK_YML = "playbook.yml";
    public static final String PLAYBOOK_TEMPLATE = stream2string(ANSIBLE_DIR + "/" + PLAYBOOK_YML + ".hbs");

    public static final String INSTALL_LOCAL_SH = "install_local.sh";
    public static final String INSTALL_LOCAL_TEMPLATE = stream2string(ANSIBLE_DIR + "/" + INSTALL_LOCAL_SH + ".hbs");

    public static final String[] BUBBLE_SCRIPTS = {
            "run.sh", "bubble_common", "bubble",
            "bget", "bpost", "bposte", "bput", "bpute", "bdelete",
            "bscript", "bmodel", "bencrypt", "bdecrypt",
            "list_bubble_databases", "cleanup_bubble_databases"
    };

    public static final int MAX_ANSIBLE_TRIES = 5;
    public static final int RESTORE_KEY_LEN = 6;

    private static final long NET_LOCK_TIMEOUT = MINUTES.toSeconds(21);
    private static final long NET_DEADLOCK_TIMEOUT = MINUTES.toSeconds(20);
    private static final long DNS_TIMEOUT = MINUTES.toMillis(60);

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AnsibleRoleDAO roleDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanDAO planDAO;

    @Autowired private NotificationService notificationService;
    @Autowired private NodeService nodeService;
    @Autowired private GeoService geoService;
    @Autowired private AnsiblePrepService ansiblePrep;
    @Autowired private RestoreService restoreService;

    @Autowired private RedisService redisService;
    @Getter(lazy=true) private final RedisService networkLocks = redisService.prefixNamespace(getClass().getSimpleName()+"_lock_");
    @Getter(lazy=true) private final RedisService networkSetupStatus = redisService.prefixNamespace(getClass().getSimpleName()+"_status_");

    public BubbleNode newNode(NewNodeNotification nn) {
        log.info("newNode starting:\n"+json(nn));
        ComputeServiceDriver computeDriver = null;
        BubbleNode node = null;
        String lock = nn.getLock();
        NodeProgressMeter progressMeter = null;
        try {
            progressMeter = new NodeProgressMeter(nn, getNetworkSetupStatus());
            progressMeter.write(METER_TICK_CONFIRMING_NETWORK_LOCK);

            if (!confirmLock(nn.getNetwork(), lock)) {
                progressMeter.error(METER_ERROR_CONFIRMING_NETWORK_LOCK);
                return die("newNode: Error confirming network lock");
            }

            progressMeter.write(METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN);
            final BubbleNetwork network = networkDAO.findByUuid(nn.getNetwork());
            if (network.getState() != BubbleNetworkState.setup) {
                progressMeter.error(METER_ERROR_NETWORK_NOT_READY_FOR_SETUP);
                return die("newNode: network is not in 'setup' state: "+network.getState());
            }
            final BubbleNode thisNode = configuration.getThisNode();
            if (thisNode == null || !thisNode.hasUuid() || thisNode.getNetwork() == null) {
                progressMeter.error(METER_ERROR_NO_CURRENT_NODE_OR_NETWORK);
                return die("newNode: thisNode not set or has no network");
            }

            BubbleNodeKey sageKey = nodeKeyDAO.findFirstByNode(thisNode.getUuid());
            if (sageKey == null) {
                sageKey = nodeKeyDAO.create(new BubbleNodeKey(thisNode));
            }

            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final Account account = accountDAO.findByUuid(network.getAccount());

            final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());

            // ensure AccountPlan has been paid for
            if (!accountPlan.enabled()) {
                progressMeter.error(METER_ERROR_PLAN_NOT_ENABLED);
                return die("newNode: accountPlan is not enabled: "+accountPlan.getUuid());
            }

            // enforce network size limit, if this is an automated request
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            final List<BubbleNode> peers = nodeDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());
            if (peers.size() >= plan.getNodesIncluded() && nn.automated()) {
                // automated requests to go past network limit are not honored
                progressMeter.error(METER_ERROR_PEER_LIMIT_REACHED);
                return die("newNode: peer limit reached ("+plan.getNodesIncluded()+")");
            }

            final CloudService cloud = findServiceOrDelegate(nn.getCloud());
            computeDriver = cloud.getComputeDriver(configuration);

            final CloudService nodeCloud = cloudDAO.findByAccountAndName(network.getAccount(), cloud.getName());
            if (nodeCloud == null) {
                progressMeter.error(METER_ERROR_NODE_CLOUD_NOT_FOUND);
                return die("newNode: node cloud not found: "+cloud.getName()+" for account "+network.getAccount());
            }

            progressMeter.write(METER_TICK_CREATING_NODE);
            node = nodeDAO.create(new BubbleNode()
                    .setHost(nn.getHost())
                    .setState(BubbleNodeState.created)
                    .setSageNode(nn.fork() ? null : configuration.getThisNode().getUuid())
                    .setNetwork(network.getUuid())
                    .setDomain(network.getDomain())
                    .setAccount(network.getAccount())
                    .setSizeType(network.getComputeSizeType())
                    .setSize(computeDriver.getSize(network.getComputeSizeType()).getInternalName())
                    .setCloud(nodeCloud.getUuid())
                    .setRegion(nn.getRegion()));

            // if we are forking, we will be our own sage
            final BubbleNode sageNode;
            if (nn.fork()) {
                node.setSageNode(node.getUuid());
                node = nodeDAO.update(node);
                sageNode = node;
            } else {
                sageNode = thisNode;
            }

            final File bubbleJar = configuration.getBubbleJar();
            if (!bubbleJar.exists()) {
                progressMeter.error(METER_ERROR_BUBBLE_JAR_NOT_FOUND);
                return die("newNode: bubble.jar not found");
            }

            @Cleanup("delete") final TempDir automation = new TempDir();
            final File bubbleFilesDir = mkdirOrDie(new File(abs(automation) + "/roles/bubble/files"));

            final List<AnsibleRole> roles = roleDAO.findByAccountAndNames(account, domain.getRoles());
            if (roles.size() != domain.getRoles().length) {
                progressMeter.error(METER_ERROR_ROLES_NOT_FOUND);
                return die("newNode: error finding ansible roles");
            }

            // build automation directory for this run
            final ValidationResult errors = new ValidationResult();
            final File roleTgzDir = mkdirOrDie(new File(abs(bubbleFilesDir), "role_tgz"));

            progressMeter.write(METER_TICK_LAUNCHING_NODE);
            node.setState(BubbleNodeState.starting);
            nodeDAO.update(node);

            // Start the cloud compute instance
            node.setState(BubbleNodeState.booting);
            nodeDAO.update(node);
            node = computeDriver.start(node);
            node.setState(BubbleNodeState.booted);
            nodeDAO.update(node);

            // Sanity check that it came up OK
            if (!node.hasIp4() || !node.hasSshKey()) {
                progressMeter.error(METER_ERROR_NO_IP_OR_SSH_KEY);
                final String message = "newNode: node booted but has no IP or SSH key";
                killNode(node, message);
                return die(message);
            }

            // Prepare ansible roles
            // We must wait until after server is started, because some roles require ip4 in vars
            progressMeter.write(METER_TICK_PREPARING_ROLES);
            final Map<String, Object> ctx = ansiblePrep.prepAnsible(automation, bubbleFilesDir, account, network, node, roles, errors, roleTgzDir, nn.fork(), nn.getRestoreKey());
            if (errors.isInvalid()) {
                progressMeter.error(METER_ERROR_ROLE_VALIDATION_ERRORS);
                throw new MultiViolationException(errors.getViolationBeans());
            }

            // Create DNS A and AAAA records for node
            progressMeter.write(METER_TICK_WRITING_DNS_RECORDS);
            final CloudService dnsService = cloudDAO.findByUuid(domain.getPublicDns());
            dnsService.getDnsDriver(configuration).setNode(node);

            progressMeter.write(METER_TICK_PREPARING_INSTALL);
            node.setState(BubbleNodeState.preparing_install);
            nodeDAO.update(node);

            // This node is on our network, or is the very first server. We must run ansible on it ourselves.
            // write playbook file
            writeFile(automation, ctx, PLAYBOOK_YML, PLAYBOOK_TEMPLATE);

            // write inventory file
            final File inventory = new File(automation, "hosts");
            final File sshKeyFile = secureFile(automation, ".ssh_key", node.getSshKey().getSshPrivateKey());
            toFile(inventory, "[bubble]\n127.0.0.1"
                    + " ansible_python_interpreter=/usr/bin/python3"
                    + " ansible_ssh_private_key_file=" +abs(sshKeyFile)+"\n");

            // write jar file
            copyFile(bubbleJar, new File(bubbleFilesDir, "bubble.jar"));

            // write scripts
            final File scriptsDir = mkdirOrDie(new File(bubbleFilesDir, "scripts"));
            for (String script : BUBBLE_SCRIPTS) {
                toFile(new File(scriptsDir, script), stream2string("scripts/"+script));
            }

            // write self_node.json file
            writeFile(bubbleFilesDir, null, SELF_NODE_JSON, json(node
                    .setPlan(plan)
                    .setRestoreKey(nn.getRestoreKey())));

            // write sage_node.json file
            writeFile(bubbleFilesDir, null, SAGE_NODE_JSON, json(BubbleNode.sageMask(sageNode)));
            writeFile(bubbleFilesDir, null, SAGE_KEY_JSON, json(BubbleNodeKey.sageMask(sageKey)));

            // write install_local.sh script
            final File installLocalScript = writeFile(automation, ctx, INSTALL_LOCAL_SH, INSTALL_LOCAL_TEMPLATE);
            chmod(installLocalScript, "500");

            // ensure this hostname is visible in our DNS and in public DNS,
            // or else node can't create its own letsencrypt SSL cert
            progressMeter.write(METER_TICK_AWAITING_DNS);
            node.setState(BubbleNodeState.awaiting_dns);
            nodeDAO.update(node);

            // ensure it resolves authoritatively first, if anyone else asks about it, they might
            // cache the fact that it doesn't exist for a long time
            final DnsServiceDriver dnsDriver = dnsService.getDnsDriver(configuration);
            dnsDriver.ensureResolvable(domain, node, DNS_TIMEOUT);

            progressMeter.write(METER_TICK_STARTING_INSTALL);
            node.setState(BubbleNodeState.installing);
            nodeDAO.update(node);

            // run ansible
            final String sshArgs = "-o UserKnownHostsFile=/dev/null "
                    + "-o StrictHostKeyChecking=no "
                    + "-o PreferredAuthentications=publickey "
                    + "-i " + abs(sshKeyFile);
            final String sshTarget = node.getUser() + "@" + node.getIp4();

            boolean setupOk = false;
            final String nodeUser = node.getUser();
            final String script = getAnsibleSetupScript(automation, sshArgs, nodeUser, sshTarget);
            log.info("newNode: running script:\n"+script);
            for (int i=0; i<MAX_ANSIBLE_TRIES; i++) {
                sleep((i+1) * SECONDS.toMillis(5), "waiting to try ansible setup");
                try {
                    final CommandResult result = ansibleSetup(script, progressMeter);
                    // .... wait for ansible ...
                    if (!result.isZeroExitStatus()) {
                        return die("newNode: error in setup:\nstdout=" + result.getStdout() + "\nstderr=" + result.getStderr());
                    }
                    setupOk = true;
                    break;
                } catch (Exception e) {
                    log.error("newNode: error running ansible: "+e);
                    progressMeter.reset();
                }
            }
            if (!setupOk) return die("newNode: error setting up, all retries failed for node: "+node.getUuid());

            // we are good.
            final BubbleNetworkState finalState = nn.hasRestoreKey() ? BubbleNetworkState.restoring : BubbleNetworkState.running;
            if (network.getState() != finalState) {
                network.setState(finalState);
                networkDAO.update(network);
            }
            node.setState(BubbleNodeState.running);
            nodeDAO.update(node);
            progressMeter.completed();

        } catch (Exception e) {
            log.error("newNode: "+e, e);
            if (node != null) {
                node.setState(BubbleNodeState.unknown_error);
                nodeDAO.update(node);
                progressMeter.error(METER_UNKNOWN_ERROR);
                killNode(node, "error: "+e);
            }
            return die("newNode: "+e, e);

        } finally {
            if (computeDriver != null) {
                try {
                    computeDriver.cleanupStart(node);
                } catch (Exception e) {
                    log.warn("newNode: compute.cleanupStart error: "+e, e);
                }
            }
            if (progressMeter != null) closeQuietly(progressMeter);
            unlockNetwork(nn.getNetwork(), lock);
        }
        return node;
    }

    public CommandResult ansibleSetup(String script, OutputStream progressMeter) throws IOException {
        return CommandShell.exec(new Command(new CommandLine("/bin/bash")
                .addArgument("-c")
                .addArgument(script, false))
                .setOut(progressMeter)
                .setCopyToStandard(configuration.testMode()));
    }

    protected String getAnsibleSetupScript(TempDir automation, String sshArgs, String nodeUser, String sshTarget) {
        return "cd " + abs(automation) + " && " +

                // rsync ansible dir to remote host
                "echo '" + METER_TICK_COPYING_ANSIBLE + "' && " +
                "rsync -az -e \"ssh " + sshArgs + "\" . "+sshTarget+ ":" + ANSIBLE_DIR + " && " +

                // run install_local.sh on remote host, installs ansible locally
                "echo '" + METER_TICK_RUNNING_ANSIBLE + "' && " +
                "ssh "+sshArgs+" "+sshTarget+" ~"+nodeUser+ "/" + ANSIBLE_DIR + "/" + INSTALL_LOCAL_SH;
    }

    private File writeFile(File dir, Map<String, Object> ctx, String filename, String templateOrData) throws IOException {
        Handlebars handlebars = configuration.getHandlebars();
        return ctx != null
                ? toFile(new File(dir, filename), HandlebarsUtil.apply(handlebars, templateOrData, ctx))
                : toFile(new File(dir, filename), templateOrData);
    }

    public BubbleNode killNode(BubbleNode node, String message) {
        if (node == null) return die("(but node was null?): "+message);
        node.setState(BubbleNodeState.error_stopping);
        node.setTag(TAG_ERROR, message);
        if (node.hasUuid()) nodeDAO.update(node);
        try {
            stopNode(node);  // kill it
        } catch (Exception e) {
            log.warn("killNode("+node.id()+"): error stopping: "+e);
        }
        node.setState(BubbleNodeState.error_stopped);
        if (node.hasUuid()) nodeDAO.update(node);
        return node;
    }

    protected String lockNetwork(String network) {
        log.info("lockNetwork: locking "+network);
        final String lock = getNetworkLocks().lock(network, NET_LOCK_TIMEOUT, NET_DEADLOCK_TIMEOUT);
        log.info("lockNetwork: locked "+network);
        return lock;
    }

    protected boolean confirmLock(String network, String lock) {
        return getNetworkLocks().confirmLock(network, lock);
    }

    protected void unlockNetwork(String network, String lock) {
        log.info("lockNetwork: unlocking "+network);
        getNetworkLocks().unlock(network, lock);
        log.info("lockNetwork: unlocked "+network);
    }

    public BubbleNode stopNode(BubbleNode node) {
        log.info("stopNode: stopping "+node.id());
        final CloudService cloud = cloudDAO.findByUuid(node.getCloud());
        return nodeService.stopNode(cloud.getComputeDriver(configuration), node);
    }

    public boolean isReachable(BubbleNode node) {
        final String prefix = "isReachable(" + node.id() + "): ";
        try {
            log.info(prefix+"starting");
            final NotificationReceipt receipt = notificationService.notify(node.getAccount(), node, NotificationType.health_check, null);
            if (receipt == null) {
                log.info(prefix+" health_check failed, checking via cloud");
                final CloudService cloud = cloudDAO.findByUuid(node.getCloud());
                if (cloud == null) {
                    log.warn(prefix+"cloud not found: "+node.getCloud());
                    return false;
                }
                final BubbleNode status = cloud.getComputeDriver(configuration).status(node);
                if (status != null) {
                    final BubbleNodeState state = status.getState();
                    if (state != null && state.active()) {
                        log.info(prefix + "cloud status was: " + state + ", returning true");
                        return true;
                    }
                }
            }
            log.warn(prefix+"no way of reaching node, returning false");
            return false;

        } catch (Exception e) {
            log.warn(prefix+e);
            return false;
        }
    }

    public NewNodeNotification startNetwork(BubbleNetwork network, NetLocation netLocation) {

        if (configuration.paymentsEnabled()) {
            final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(network.getAccount(), network.getUuid());
            if (accountPlan == null) throw invalidEx("err.accountPlan.notFound");
            if (accountPlan.disabled()) throw invalidEx("err.accountPlan.disabled");
        }

        String lock = null;
        try {
            lock = lockNetwork(network.getUuid());

            // sanity checks
            if (!nodeDAO.findByNetwork(network.getUuid()).isEmpty()) {
                throw invalidEx("err.network.alreadyStarted");
            }
            if (!network.getState().canStartNetwork()) {
                throw invalidEx("err.network.cannotStartInCurrentState");
            }

            network.setState(BubbleNetworkState.setup);
            networkDAO.update(network);

            // ensure NS records for network are in DNS
            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
            dns.getDnsDriver(configuration).setNetwork(network);

            final CloudAndRegion cloudAndRegion = geoService.selectCloudAndRegion(network, netLocation);
            final String host = network.fork() ? network.getForkHost() : newNodeHostname();
            final NewNodeNotification newNodeRequest = new NewNodeNotification()
                    .setAccount(network.getAccount())
                    .setNetwork(network.getUuid())
                    .setDomain(network.getDomain())
                    .setFork(network.fork())
                    .setHost(host)
                    .setFqdn(host + "." + network.getNetworkDomain())
                    .setCloud(cloudAndRegion.getCloud().getUuid())
                    .setRegion(cloudAndRegion.getRegion().getInternalName())
                    .setLock(lock);

            // start background process to create node
            backgroundNewNode(newNodeRequest, lock);

            return newNodeRequest;

        } catch (SimpleViolationException e) {
            throw e;

        } catch (Exception e) {
            return die("startNetwork: "+e, e);
        }
    }

    public NewNodeNotification restoreNetwork(BubbleNetwork network, String cloud, String region, Request req) {

        final NetLocation netLocation = (region != null)
                ? NetLocation.fromCloudAndRegion(cloud, region)
                : NetLocation.fromIp(getRemoteHost(req));

        String lock = null;
        try {
            lock = lockNetwork(network.getUuid());

            // sanity checks
            final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
            if (!nodes.isEmpty()) {
                throw invalidEx("err.network.restore.nodesExist");
            }
            if (network.getState() != BubbleNetworkState.stopped) {
                throw invalidEx("err.network.restore.notStopped");
            }
            network.setState(BubbleNetworkState.setup);
            networkDAO.update(network);

            // ensure NS records for network are in DNS
            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
            dns.getDnsDriver(configuration).setNetwork(network);

            final CloudAndRegion cloudAndRegion = geoService.selectCloudAndRegion(network, netLocation);
            final String host = network.fork() ? network.getForkHost() : newNodeHostname();
            final String restoreKey = randomAlphanumeric(RESTORE_KEY_LEN).toUpperCase();
            restoreService.registerRestore(restoreKey, new NetworkKeys());
            final NewNodeNotification newNodeRequest = new NewNodeNotification()
                    .setAccount(network.getAccount())
                    .setNetwork(network.getUuid())
                    .setDomain(network.getDomain())
                    .setRestoreKey(restoreKey)
                    .setHost(host)
                    .setFqdn(host+"."+network.getNetworkDomain())
                    .setCloud(cloudAndRegion.getCloud().getUuid())
                    .setRegion(cloudAndRegion.getRegion().getInternalName())
                    .setLock(lock);

            // start background process to create node
            backgroundNewNode(newNodeRequest, lock);

            return newNodeRequest;

        } catch (Exception e) {
            return die("startNetwork: "+e, e);
        }
    }

    public void backgroundNewNode(NewNodeNotification newNodeRequest) {
        backgroundNewNode(newNodeRequest, null);
    }

    public void backgroundNewNode(NewNodeNotification newNodeRequest, final String existingLock) {
        final AtomicReference<String> lock = new AtomicReference<>(existingLock);
        daemon(new NodeLauncher(newNodeRequest, lock, this));
    }

    public boolean stopNetwork(BubbleNetwork network) {
        log.info("stopNetwork: stopping "+network.getNetworkDomain());
        final String networkUuid = network.getUuid();

        network = networkDAO.findByUuid(networkUuid);
        if (network == null) throw notFoundEx(networkUuid);

        // are any of them still alive?
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (nodes.isEmpty()) {
            // nothing is running... what do we need to stop?
            log.warn("stopNetwork: no nodes running");
        }

        network.setState(BubbleNetworkState.stopping);
        networkDAO.update(network);

        final ValidationResult validationResult = new ValidationResult();

        // todo: parallel shutdown?
        // stop all nodes in network
        nodes.forEach(node -> {
            try {
                stopNode(node);
                log.info("stopNetwork: stopped node "+node.id());
            } catch (Exception e) {
                validationResult.addViolation("err.node.shutdownFailed", "Node shutdown failed: " + node.getUuid() + "/" + node.getIp4() + ": " + e);
            }
        });

        if (validationResult.isInvalid()) throw invalidEx(validationResult);

        network.setState(BubbleNetworkState.stopped);
        networkDAO.update(network);

        // delete nodes in network
        nodes.forEach(node -> nodeDAO.delete(node.getUuid()));

        log.info("stopNetwork: stopped " + network.getNetworkDomain());
        return true;
    }

    protected CloudService findServiceOrDelegate(String cloudUuid) {
        CloudService cloud = cloudDAO.findByUuid(cloudUuid);
        if (!cloud.delegated()) return cloud;

        // was it delegated to us?
        final CloudCredentials credentials = cloud.getCredentials();
        if (credentials == null || !credentials.isDelegate()) {
            return die("findServiceOrDelegate: invalid CloudService delegation: "+cloud.getUuid());
        }
        if (configuration.getThisNode().getUuid().equals(credentials.getDelegateNode())) {
            // delegated to ourselves. OK, use our cloud
            final String delegated = cloud.getDelegated();
            cloud = cloudDAO.findByUuid(delegated);
            if (cloud == null) return die("findServiceOrDelegate: delegated cloud not found: "+ delegated);
        } else {
            return die("findServiceOrDelegate: invalid delegation: "+credentials.getDelegateNode());
        }
        return cloud;
    }

    public NodeProgressMeterTick getLaunchStatus(String accountUuid, String uuid) {
        final String json = getNetworkSetupStatus().get(getProgressMeterKey(uuid, accountUuid));
        if (json == null) return null;
        try {
            final NodeProgressMeterTick tick = json(json, NodeProgressMeterTick.class);
            if (!tick.hasAccount() || !tick.getAccount().equals(accountUuid)) {
                log.warn("getLaunchStatus: tick.account != accountUuid, returning null");
                return null;
            }
            log.warn("getLaunchStatus: returning tick: "+json(tick, COMPACT_MAPPER));
            return tick.setPattern(null);
        } catch (Exception e) {
            return die("getLaunchStatus: "+e);
        }
    }

    public List<NodeProgressMeterTick> listLaunchStatuses(String accountUuid) {
        final RedisService stats = getNetworkSetupStatus();
        final List<NodeProgressMeterTick> ticks = new ArrayList<>();
        for (String key : stats.keys(getProgressMeterPrefix(accountUuid)+"*")) {
            final String json = stats.get_withPrefix(key);
            if (json != null) {
                try {
                    ticks.add(json(json, NodeProgressMeterTick.class));
                } catch (Exception e) {
                    log.warn("currentTicks (bad json?): "+e);
                }
            }
        }
        return ticks;
    }

}
