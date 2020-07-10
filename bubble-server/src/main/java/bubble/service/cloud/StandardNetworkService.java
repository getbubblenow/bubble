/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.CloudAndRegion;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.AccountSshKeyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.*;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AccountSshKey;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.cloud.notify.NotificationType;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.RestoreService;
import bubble.service.bill.PromotionService;
import bubble.service.cloud.job.NodeDnsJob;
import bubble.service.cloud.job.NodeJobException;
import bubble.service.cloud.job.NodeStartJob;
import bubble.service.notify.NotificationService;
import bubble.service.packer.PackerService;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.daemon.AwaitResult;
import org.cobbzilla.util.daemon.DaemonThreadFactory;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.util.system.SleepInterruptedException;
import org.cobbzilla.wizard.api.ApiException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.*;
import static bubble.client.BubbleNodeClient.nodeBaseUri;
import static bubble.dao.bill.AccountPlanDAO.PURCHASE_DELAY;
import static bubble.model.cloud.BubbleNode.TAG_ERROR;
import static bubble.server.BubbleConfiguration.DEBUG_NODE_INSTALL_FILE;
import static bubble.server.BubbleConfiguration.ENV_DEBUG_NODE_INSTALL;
import static bubble.service.boot.StandardSelfNodeService.*;
import static bubble.service.cloud.NodeLaunchException.*;
import static bubble.service.cloud.NodeProgressMeterConstants.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.Await.awaitAll;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.closeQuietly;
import static org.cobbzilla.util.system.CommandShell.chmod;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class StandardNetworkService implements NetworkService {

    public static final String PLAYBOOK_YML = "playbook.yml";
    public static final String PLAYBOOK_TEMPLATE = stream2string(ANSIBLE_DIR + "/" + PLAYBOOK_YML + ".hbs");

    public static final String INSTALL_LOCAL_SH = "install_local.sh";
    public static final String INSTALL_LOCAL_TEMPLATE = stream2string(ANSIBLE_DIR + "/" + INSTALL_LOCAL_SH + ".hbs");

    public static final int MAX_ANSIBLE_TRIES = 5;
    public static final int RESTORE_KEY_LEN = 6;

    private static final long NET_LOCK_TIMEOUT = MINUTES.toMillis(11);
    private static final long NET_DEADLOCK_TIMEOUT = MINUTES.toMillis(10);
    private static final long PLAN_ENABLE_TIMEOUT = PURCHASE_DELAY + SECONDS.toMillis(10);
    private static final long NODE_START_JOB_TIMEOUT = MINUTES.toMillis(10);
    private static final long NODE_START_JOB_AWAIT_SLEEP = SECONDS.toMillis(2);
    private static final long NODE_READY_TIMEOUT = MINUTES.toMillis(6);

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountSshKeyDAO sshKeyDAO;
    @Autowired @Getter private BubbleNetworkDAO networkDAO;
    @Autowired @Getter private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private AccountMessageDAO accountMessageDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private PromotionService promoService;

    @Autowired private NotificationService notificationService;
    @Autowired private NodeService nodeService;
    @Autowired private GeoService geoService;
    @Autowired private AnsiblePrepService ansiblePrep;
    @Autowired private PackerService packerService;
    @Autowired private RestoreService restoreService;
    @Autowired private NodeLaunchMonitor launchMonitor;

    @Autowired private RedisService redisService;
    @Getter(lazy=true) private final RedisService networkLocks = redisService.prefixNamespace(getClass().getSimpleName()+"_net_lock_");
    @Getter(lazy=true) private final RedisService nodeKillLocks = redisService.prefixNamespace(getClass().getSimpleName()+"_node_kill_lock_");

    @NonNull public BubbleNode newNode(@NonNull final NewNodeNotification nn,
                                       NodeLaunchMonitor launchMonitor) {
        final long start = now();
        log.info("newNode starting:\n"+json(nn));
        ComputeServiceDriver computeDriver = null;
        BubbleNode node = null;
        String lock = nn.getLock();
        NodeProgressMeter progressMeter = null;
        final BubbleNetwork network = nn.getNetworkObject();
        final ExecutorService backgroundJobs = DaemonThreadFactory.fixedPool(3);
        boolean killNode = false;
        try {
            progressMeter = launchMonitor.getProgressMeter(nn);
            progressMeter.write(METER_TICK_CONFIRMING_NETWORK_LOCK);

            if (!confirmNetLock(nn.getNetwork(), lock)) {
                progressMeter.error(METER_ERROR_CONFIRMING_NETWORK_LOCK);
                return launchFailureCanRetry("newNode: Error confirming network lock");
            }

            progressMeter.write(METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN);
            if (network.getState() != BubbleNetworkState.starting) {
                if (network.getState() == BubbleNetworkState.stopped) {
                    log.info("newNode: network was stopped, starting: "+network.getUuid());
                    networkDAO.update(network.setState(BubbleNetworkState.starting));
                } else {
                    progressMeter.error(METER_ERROR_NETWORK_NOT_READY_FOR_SETUP);
                    return fatalLaunchFailure("newNode: network is not in 'setup' state: " + network.getState());
                }
            }
            final BubbleNode thisNode = configuration.getThisNode();
            if (thisNode == null || !thisNode.hasUuid() || thisNode.getNetwork() == null) {
                progressMeter.error(METER_ERROR_NO_CURRENT_NODE_OR_NETWORK);
                return fatalLaunchFailure("newNode: thisNode not set or has no network");
            }

            BubbleNodeKey sageKey = nodeKeyDAO.findFirstByNode(thisNode.getUuid());
            if (sageKey == null) {
                sageKey = nodeKeyDAO.create(new BubbleNodeKey(thisNode));
            }

            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final Account account = accountDAO.findByUuid(network.getAccount());

            final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());

            // ensure AccountPlan is enabled
            if (!accountPlan.enabled()) {
                progressMeter.error(METER_ERROR_PLAN_NOT_ENABLED);
                return fatalLaunchFailure("newNode: accountPlan is not enabled: "+accountPlan.getUuid());
            }

            // enforce network size limit, if this is an automated request
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            final List<BubbleNode> peers = nodeDAO.findRunningByAccountAndNetwork(account.getUuid(), network.getUuid());
            if (peers.size() >= plan.getNodesIncluded() && nn.automated()) {
                // automated requests to go past network limit are not honored
                progressMeter.error(METER_ERROR_PEER_LIMIT_REACHED);
                return fatalLaunchFailure("newNode: peer limit reached ("+plan.getNodesIncluded()+")");
            }

            final CloudService cloud = findServiceOrDelegate(nn.getCloud());
            computeDriver = cloud.getComputeDriver(configuration);

            final CloudService nodeCloud = cloudDAO.findByAccountAndName(network.getAccount(), cloud.getName());
            if (nodeCloud == null) {
                progressMeter.error(METER_ERROR_NODE_CLOUD_NOT_FOUND);
                return fatalLaunchFailure("newNode: node cloud not found: "+cloud.getName()+" for account "+network.getAccount());
            }

            progressMeter.write(METER_TICK_CREATING_NODE);
            node = nodeDAO.create(new BubbleNode()
                    .setHost(nn.getHost())
                    .setState(BubbleNodeState.created)
                    .setSageNode(nn.fork() ? null : configuration.getThisNode().getUuid())
                    .setSslPort(network.getSslPort())
                    .setNetwork(network.getUuid())
                    .setDomain(network.getDomain())
                    .setInstallType(network.getInstallType())
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

            @Cleanup("delete") final TempDir automation = new TempDir();
            final File bubbleFilesDir = mkdirOrDie(new File(abs(automation) + "/roles/bubble/files"));

            // build automation directory for this run
            final ValidationResult errors = new ValidationResult();

            progressMeter.write(METER_TICK_LAUNCHING_NODE);
            node.setState(BubbleNodeState.starting);
            nodeDAO.update(node);

            final List<Future<?>> jobFutures = new ArrayList<>();

            // Start the cloud compute instance
            final NodeStartJob startJob = new NodeStartJob(node, computeDriver);
            jobFutures.add(backgroundJobs.submit(startJob));

            // Create DNS records for node
            final NodeDnsJob dnsJob = new NodeDnsJob(cloudDAO, domain, network, node, configuration);
            jobFutures.add(backgroundJobs.submit(dnsJob));

            // Prepare ansible roles
            // We must wait until after server is started, because some roles require ip4 in vars
            try {
                node.waitForIpAddresses();
            } catch (TimeoutException e) {
                launchFailureCanRetry(node, e, "waitForIpAddresses error");
            }
            progressMeter.write(METER_TICK_PREPARING_ROLES);
            final Map<String, Object> ctx = ansiblePrep.prepAnsible(
                    automation, bubbleFilesDir, account, network, node, computeDriver,
                    errors, nn.fork(), nn.getRestoreKey());
            if (errors.isInvalid()) {
                progressMeter.error(METER_ERROR_ROLE_VALIDATION_ERRORS);
                fatalLaunchFailure(node, new MultiViolationException(errors.getViolationBeans()));
            }

            progressMeter.write(METER_TICK_PREPARING_INSTALL);
            node.setState(BubbleNodeState.preparing_install);
            nodeDAO.update(node);

            // This node is on our network, or is the very first server. We must run ansible on it ourselves.
            // write playbook file
            writeFile(automation, ctx, PLAYBOOK_YML, PLAYBOOK_TEMPLATE);

            // write inventory file
            final File inventory = new File(automation, "hosts");
            final File sshKeyFile = packerService.getPackerPrivateKey();
            toFile(inventory, "[bubble]\n127.0.0.1"
                    + " ansible_python_interpreter=/usr/bin/python3\n");

            // write SSH key, if present
            if (network.hasSshKey()) {
                final File sshPubKeyFile = new File(bubbleFilesDir, "admin_ssh_key.pub");
                final AccountSshKey sshKey = sshKeyDAO.findByAccountAndId(network.getAccount(), network.getSshKey());
                if (sshKey == null) throw invalidEx("err.sshPublicKey.notFound");
                // add a newline before in case authorized_keys file does not end in a new line
                // add a newline after so keys appended later will be OK
                toFile(sshPubKeyFile, "\n"+sshKey.getSshPublicKey()+"\n");
            }
            copyScripts(bubbleFilesDir);

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

            // run ansible
            final String sshArgs = "-o UserKnownHostsFile=/dev/null "
                    + "-o StrictHostKeyChecking=no "
                    + "-o PreferredAuthentications=publickey "
                    + "-i " + abs(sshKeyFile);
            final String sshTarget = node.getUser() + "@" + node.getIp4();

            boolean setupOk = false;
            final String nodeUser = node.getUser();
            final String script = getAnsibleSetupScript(automation, sshArgs, nodeUser, sshTarget);

            log.info("newNode: awaiting background jobs...");
            final AwaitResult<Object> awaitResult = awaitAll(jobFutures, NODE_START_JOB_TIMEOUT, NODE_START_JOB_AWAIT_SLEEP, new NodeLaunchAwait(progressMeter));
            if (!awaitResult.allSucceeded()) {
                log.warn("newNode: some background jobs failed: "+awaitResult.getFailures().values());
                final Exception firstException = awaitResult.getFailures().values().iterator().next();
                if (firstException instanceof NodeJobException) {
                    progressMeter.error(((NodeJobException) firstException).getMeterError());
                } else {
                    progressMeter.error(METER_START_OR_DNS_ERROR);
                }
                return launchFailureCanRetry(node, "newNode: error in setup:" + awaitResult.getFailures().values());
            }
            waitForDebugger(script);
            long configStart = now();

            progressMeter.write(METER_TICK_STARTING_INSTALL);
            node.setState(BubbleNodeState.installing);
            nodeDAO.update(node);

            log.info("newNode: running script:\n"+script);
            for (int i=0; !setupOk && i<MAX_ANSIBLE_TRIES; i++) {
                sleep((i+1) * SECONDS.toMillis(5), "waiting to try ansible setup");
                // Use .uncloseable() because it the command fails due to connection timeout/refused,
                // the output stream is closed; if a retry succeeds, there's no output to the progressMeter
                final CommandResult result = ansibleSetup(script, progressMeter.uncloseable());
                // .... wait for ansible ...
                if (!result.isZeroExitStatus()) {
                    if (result.getStderr().contains("Connection timed out")
                            || result.getStderr().contains("SSH connection timeout")
                            || result.getStderr().contains("Connection refused")
                            || result.getStderr().contains("connection unexpectedly closed")) {
                        log.warn("newNode: SSH connection error: " + result.getStderr());
                        continue;
                    }
                    return launchFailureCanRetry(node, "newNode: error in setup:\nstdout=" + result.getStdout() + "\nstderr=" + result.getStderr());
                }
                log.info("newNode: started in " + formatDuration(now() - start));
                log.info("newNode: initialized in " + formatDuration(now() - configStart));
                setupOk = true;
            }
            if (!setupOk) return launchFailureCanRetry(node, "newNode: error setting up, all retries failed for node: "+node.getUuid());

            // wait for node to be ready
            if (node.getInstallType() == AnsibleInstallType.node) {
                final long readyStart = now();
                boolean ready = false;
                Exception lastEx = null;
                final String readyUri = nodeBaseUri(node, configuration) + AUTH_ENDPOINT + EP_READY;
                int i = 1;
                while (now() - readyStart < NODE_READY_TIMEOUT) {
                    sleep(SECONDS.toMillis(2), "newNode: waiting for node (" + node.id() + ") to be ready");
                    log.debug("newNode: waiting for node (" + node.id() + ") to be ready via "+readyUri);
                    progressMeter.write("READY-CHECK-"+(i++)+" /auth/ready for node: "+node.id());
                    launchMonitor.touch(network.getUuid());
                    try {
                        final HttpResponseBean readyResponse = HttpUtil.getResponse(readyUri);
                        if (readyResponse.isOk()) {
                            log.info("newNode: node (" + node.id() + ") is ready!");
                            ready = true;
                            break;
                        } else {
                            log.debug("newNode: waiting for node (" + node.id() + ") /auth/ready call failed: "+readyResponse.getStatus());
                        }
                    } catch (Exception e) {
                        log.warn("newNode: node (" + node.id() + ") error checking if ready: " + shortError(e));
                        lastEx = e;
                    }
                }
                if (!ready) {
                    if (lastEx != null) {
                        var responseStatus = "";
                        if (lastEx instanceof ApiException) {
                            responseStatus = " (HTTP status: " + ((ApiException) lastEx).getResponse().status + ")";
                        }
                        log.warn("newNode: the last exception in checking if ready" + responseStatus, lastEx);
                    }
                    return launchFailureCanRetry(node, "newNode: timeout waiting for node (" + node.id() + ") to be ready");
                }
            }

            // we are good.
            launchMonitor.touch(network.getUuid());
            final BubbleNetworkState finalState = nn.hasRestoreKey() ? BubbleNetworkState.restoring : BubbleNetworkState.running;
            if (network.getState() != finalState) {
                network.setState(finalState);
                networkDAO.update(network);
            }
            node.setState(BubbleNodeState.running);
            nodeDAO.update(node);
            progressMeter.completed();
            log.info("newNode: ready in "+formatDuration(now() - start));

        } catch (Exception e) {
            if (e instanceof SleepInterruptedException) {
                log.warn("newNode: interrupted!");
            } else {
                log.error("newNode: " + e, e);
            }
            killNode = node != null;
            if (e instanceof NodeLaunchException) throw (NodeLaunchException) e;
            if (e instanceof SleepInterruptedException) launchInterrupted("newNode: interrupted: "+shortError(e));
            return die("newNode: "+e, e);

        } finally {
            backgroundJobs.shutdownNow();
            if (computeDriver != null) {
                try {
                    computeDriver.cleanupStart(node);
                } catch (Exception e) {
                    log.warn("newNode: compute.cleanupStart error: "+e, e);
                }
            }

            if (node != null && (killNode || !node.isRunning())) {
                node.setState(BubbleNodeState.unknown_error);
                nodeDAO.update(node);
                if (!progressMeter.hasError()) progressMeter.error(METER_UNKNOWN_ERROR);
                killNode(node, "error: node not running: "+node.id()+": "+node.getState());
            }

            if (progressMeter != null) {
                if (!progressMeter.success() && configuration.paymentsEnabled()) {
                    final AccountPlan accountPlan = accountPlanDAO.findByNetwork(nn.getNetwork());
                    if (accountPlan != null) {
                        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
                        if (plan != null) {
                            promoService.applyLaunchFailurePromo(nn.getAccount(), plan.getCurrency());
                        }
                    }
                }
                closeQuietly(progressMeter);
            }
            unlockNetwork(nn.getNetwork(), lock);
        }
        return node;
    }

    public void waitForDebugger(String script) {
        if (Boolean.parseBoolean(configuration.getEnvironment().get(ENV_DEBUG_NODE_INSTALL))) {
            final String msg = "waitForDebugger: debugging installation, waiting for " + abs(DEBUG_NODE_INSTALL_FILE) + " to exist";
            log.info(msg+" before running script:\n"+script);
            while (!DEBUG_NODE_INSTALL_FILE.exists()) {
                sleep(SECONDS.toMillis(10), msg);
            }
            log.info("waitForDebugger: "+abs(DEBUG_NODE_INSTALL_FILE)+" exists, continuing installation");
        }
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
        if (node == null) return die("killNode: node was null (message=" + message + ")");
        String lock = null;
        try {
            lock = lockNode(node.getUuid());
            if (nodeDAO.findByUuid(node.getUuid()) == null) {
                log.warn("killNode: node already deleted");
                return node;
            }

            node.setState(BubbleNodeState.error_stopping);
            node.setTag(TAG_ERROR, message);
            if (node.hasUuid()) nodeDAO.update(node);
            try {
                stopNode(node);  // kill it
            } catch (Exception e) {
                log.warn("killNode(" + node.id() + "): error stopping: " + e);
            }
            node.setState(BubbleNodeState.error_stopped);
            nodeDAO.update(node);
            return node;

        } finally {
            if (lock != null) unlockNode(node.getUuid(), lock);
        }
    }

    protected String lockNetwork(String network) {
        log.info("lockNetwork: locking "+network);
        final String lock = getNetworkLocks().lock(network, NET_LOCK_TIMEOUT, NET_DEADLOCK_TIMEOUT);
        log.info("lockNetwork: locked "+network);
        return lock;
    }

    protected boolean confirmNetLock(String network, String lock) {
        return getNetworkLocks().confirmLock(network, lock);
    }

    protected void unlockNetwork(String network, String lock) {
        log.info("unlockNetwork: unlocking "+network);
        getNetworkLocks().unlock(network, lock);
        log.info("unlockNetwork: unlocked "+network);
    }

    protected String lockNode(String node) {
        log.info("lockNode: locking "+node);
        final String lock = getNodeKillLocks().lock(node, NET_LOCK_TIMEOUT, NET_DEADLOCK_TIMEOUT);
        log.info("lockNode: locked "+node);
        return lock;
    }

    protected boolean confirmNodeLock(String node, String lock) {
        return getNodeKillLocks().confirmLock(node, lock);
    }

    protected void unlockNode(String node, String lock) {
        log.info("unlockNode: unlocking "+node);
        getNodeKillLocks().unlock(node, lock);
        log.info("unlockNode: unlocked "+node);
    }

    public void stopNode(BubbleNode node) {
        log.info("stopNode: stopping "+node.id());
        final CloudService cloud = cloudDAO.findByUuid(node.getCloud());
        nodeService.stopNode(cloud.getComputeDriver(configuration), node);
    }

    public boolean isReachable(BubbleNode node) {
        final String prefix = "isReachable(" + node.id() + "): ";
        try {
            log.info(prefix+"starting");
            final NotificationReceipt receipt = notificationService.notify(node, NotificationType.health_check, null);
            BubbleNodeState state = null;
            if (receipt == null) {
                log.info(prefix+"health_check failed, checking via cloud");
                final CloudService cloud = cloudDAO.findByUuid(node.getCloud());
                if (cloud == null) {
                    log.warn(prefix+"cloud not found: "+node.getCloud());
                    return false;
                }
                final BubbleNode status = cloud.getComputeDriver(configuration).status(node);
                if (status != null) {
                    state = status.getState();
                    if (state != null && state.active()) {
                        log.info(prefix + "cloud status was: " + state + ", returning true");
                        return true;
                    }
                }
            }
            log.warn(prefix+"no way of reaching node "+node.id()+" (state="+state+"), returning false");
            return false;

        } catch (Exception e) {
            log.warn(prefix+e);
            return false;
        }
    }

    public NewNodeNotification startNetwork(BubbleNetwork network, NetLocation netLocation) {

        final String accountUuid = network.getAccount();
        if (configuration.paymentsEnabled()) {
            AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(accountUuid, network.getUuid());
            if (accountPlan == null) throw invalidEx("err.accountPlan.notFound");
            final long start = now();
            while (accountPlan.disabled() && now() - start < PLAN_ENABLE_TIMEOUT) {
                sleep(100, "startNetwork: waiting for accountPlan to become enabled: "+accountUuid);
                accountPlan = accountPlanDAO.findByAccountAndNetwork(accountUuid, network.getUuid());
            }
            if (accountPlan.disabled()) throw invalidEx("err.accountPlan.disabled");
        }
        if (network.hasSshKey()) {
            final AccountSshKey key = sshKeyDAO.findByAccountAndId(network.getAccount(), network.getSshKey());
            if (key == null) throw invalidEx("err.sshPublicKey.notFound");
            if (key.expired()) throw invalidEx("err.sshPublicKey.expired");
        }

        final Account account = accountDAO.findByUuid(accountUuid);
        if (account == null) throw notFoundEx(accountUuid);

        final AccountPolicy policy = policyDAO.findSingleByAccount(accountUuid);
        if (!policy.hasVerifiedAccountContacts() && !account.admin()) {
            throw invalidEx("err.accountPlan.noVerifiedContacts");
        }

        String lock = null;
        try {
            lock = lockNetwork(network.getUuid());

            // sanity checks
            if (anyNodesActive(network)) {
                throw invalidEx("err.network.alreadyStarted");
            }
            if (!network.getState().canStart()) {
                throw invalidEx("err.network.cannotStartInCurrentState");
            }

            network.setState(BubbleNetworkState.starting);
            networkDAO.update(network);

            // ensure NS records for network are in DNS
            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
            dns.getDnsDriver(configuration).setNetwork(network);

            final CloudAndRegion cloudAndRegion = geoService.selectCloudAndRegion(network, netLocation);
            final NewNodeNotification newNodeRequest = new NewNodeNotification()
                    .setFork(network.fork())
                    .setNodeHost(network)
                    .setCloud(cloudAndRegion.getCloud().getUuid())
                    .setRegion(cloudAndRegion.getRegion().getInternalName())
                    .setLock(lock);

            // notify user that new bubble is launching
            accountMessageDAO.create(new AccountMessage()
                    .setAccount(accountUuid)
                    .setNetwork(network.getUuid())
                    .setName(network.getUuid())
                    .setMessageType(AccountMessageType.notice)
                    .setTarget(ActionTarget.network)
                    .setAction(AccountAction.start));

            // start background process to create node
            backgroundNewNode(newNodeRequest, lock);

            return newNodeRequest;

        } catch (SimpleViolationException e) {
            throw e;

        } catch (Exception e) {
            return die("startNetwork: "+e, e);
        }
    }

    public boolean anyNodesActive(BubbleNetwork network) {
        return nodeDAO.findByNetwork(network.getUuid()).stream().anyMatch(BubbleNode::isActive);
    }

    public boolean noNodesActive(BubbleNetwork network) { return !anyNodesActive(network); }

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
                throw invalidEx("err.networkRestore.nodesExist");
            }
            if (network.getState() != BubbleNetworkState.stopped) {
                throw invalidEx("err.networkRestore.notStopped");
            }
            network.setState(BubbleNetworkState.starting);
            networkDAO.update(network);

            // ensure NS records for network are in DNS
            final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
            final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
            dns.getDnsDriver(configuration).setNetwork(network);

            final CloudAndRegion cloudAndRegion = geoService.selectCloudAndRegion(network, netLocation);
            final String restoreKey = randomAlphanumeric(RESTORE_KEY_LEN).toUpperCase();
            restoreService.registerRestore(restoreKey, new NetworkKeys());
            final NewNodeNotification newNodeRequest = new NewNodeNotification()
                    .setNodeHost(network)
                    .setRestoreKey(restoreKey)
                    .setCloud(cloudAndRegion.getCloud().getUuid())
                    .setRegion(cloudAndRegion.getRegion().getInternalName())
                    .setLock(lock);

            // start background process to create node
            backgroundNewNode(newNodeRequest, lock);

            return newNodeRequest;

        } catch (SimpleViolationException e) {
            // TODO: should this go here, or just in some specific cases within above try block?
            //       also, should this go into other method here that are locking network?
            try { unlockNetwork(network.getUuid(), lock); } catch (Exception e1) { }
            log.error("startNetwork: original SimpleViolationException: ", e);
            throw e;
        } catch (Exception e) {
            return die("startNetwork: " + e, e);
        }
    }

    public void backgroundNewNode(NewNodeNotification newNodeRequest) {
        backgroundNewNode(newNodeRequest, null);
    }

    public void backgroundNewNode(NewNodeNotification newNodeRequest, final String existingLock) {
        final AtomicReference<String> lock = new AtomicReference<>(existingLock);
        daemon(new NodeLauncher(newNodeRequest, lock, this, launchMonitor));
    }

    public boolean stopNetwork(final BubbleNetwork network) {
        log.info("stopNetwork: stopping "+network.getNetworkDomain());
        if (!network.getState().canStop()) {
            log.warn("stopNetwork: canStop is false for network "+network.getUuid()+": state="+network.getState());
            return true;
        }

        networkDAO.update(network.setState(BubbleNetworkState.stopping));

        background(() -> {
            String lock = null;
            final String networkUuid = network.getUuid();
            try {
                launchMonitor.cancel(networkUuid);
                lock = lockNetwork(networkUuid);

                // are any of them still alive?
                final List<BubbleNode> nodes = nodeDAO.findByNetwork(networkUuid);
                if (nodes.isEmpty()) {
                    // nothing is running... what do we need to stop?
                    log.warn("stopNetwork: no nodes running");
                }

                if (nodes.size() == 1) {
                    final BubbleNode n = nodes.get(0);
                    if (n.isLocalCompute()) {
                        throw invalidEx("err.node.cannotStopLocalNode", "Cannot stop local node: " + n.id(), n.id());
                    }
                }

                final ValidationResult validationResult = new ValidationResult();

                // todo: parallel shutdown?
                // stop all nodes in network
                nodes.forEach(node -> {
                    try {
                        stopNode(node);
                        log.info("stopNetwork: stopped node " + node.id());
                    } catch (Exception e) {
                        validationResult.addViolation("err.node.shutdownFailed", "Node shutdown failed: " + node.getUuid() + "/" + node.getIp4() + ": " + e);
                    }
                });

                if (validationResult.isInvalid()) {
                    throw invalidEx(validationResult);
                }

                // delete nodes in network
                nodes.forEach(node -> nodeDAO.delete(node.getUuid()));

                log.info("stopNetwork: stopped " + network.getNetworkDomain());
                network.setState(BubbleNetworkState.stopped);
                networkDAO.update(network);

            } catch (RuntimeException e) {
                log.error("stopNetwork: error stopping: "+e);
                network.setState(BubbleNetworkState.error_stopping);
                networkDAO.update(network);
                throw e;

            } finally {
                if (lock != null) unlockNetwork(networkUuid, lock);
            }
        });
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

    private static class NodeLaunchAwait implements Runnable {

        private final NodeProgressMeter progressMeter;
        private int counter = 1;

        public NodeLaunchAwait(NodeProgressMeter progressMeter) { this.progressMeter = progressMeter; }

        @Override public void run() {
            try {
                progressMeter.write("AWAIT-" + (counter++) + " still awaiting background jobs...");
            } catch (IOException e) {
                log.warn("error writing to progressMeter: "+shortError(e));
            }
        }
    }

}
