package bubble.service.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanAppDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.AnsibleRole;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import bubble.service.dbfilter.DatabaseFilterService;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.Tarball;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.wizard.server.config.ErrorApiConfiguration;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bubble.service.backup.RestoreService.RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS;
import static org.cobbzilla.util.collection.HasPriority.SORT_PRIORITY;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.string.Base64.DONT_GUNZIP;

@Service @Slf4j
public class AnsiblePrepService {

    @Autowired private DatabaseFilterService dbFilter;
    @Autowired private StandardStorageService storageService;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanAppDAO planAppDAO;

    public Map<String, Object> prepAnsible(TempDir automation,
                                           File bubbleFilesDir,
                                           Account account,
                                           BubbleNetwork network,
                                           BubbleNode node,
                                           ComputeServiceDriver computeDriver,
                                           List<AnsibleRole> roles,
                                           ValidationResult errors,
                                           File tarballDir,
                                           boolean fork,
                                           String restoreKey) throws IOException, ArchiveException {
        final BubbleConfiguration c = configuration;
        if (tarballDir == null) tarballDir = automation;

        final AnsibleInstallType installType = network.getInstallType();

        roles.sort(SORT_PRIORITY);
        final List<AnsibleRole> installRoles = roles.stream()
                .filter(role -> role.shouldInstall(installType))
                .collect(Collectors.toList());

        final Map<String, Object> ctx = new HashMap<>();
        final Handlebars handlebars = c.getHandlebars();
        ctx.put("configuration", configuration);
        ctx.put("fork", fork);
        ctx.put("installType", installType.name());
        ctx.put("nodeSize", computeDriver.getSize(node.getSizeType()));
        if (restoreKey != null) {
            ctx.put("restoreKey", restoreKey);
            ctx.put("restoreTimeoutSeconds", RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS);
        }
        ctx.put("sslPort", network.getSslPort());
        ctx.put("publicBaseUri", network.getPublicUri());

        if (network.sendMetrics() && configuration.hasErrorApi()) {
            final ErrorApiConfiguration errorApi = configuration.getErrorApi();
            ctx.put("error_url", errorApi.getUrl());
            ctx.put("error_key", errorApi.getKey());
            ctx.put("error_env", node.getFqdn());
        }

        ctx.put("network", network);
        ctx.put("node", node);
        ctx.put("roles", installRoles.stream().map(AnsibleRole::getRoleName).collect(Collectors.toList()));
        ctx.put("testMode", !fork && configuration.testMode());

        // Determine which apps should be copied based on plan
        final List<BubblePlanApp> planApps;
        if (configuration.paymentsEnabled()) {
            final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());
            if (accountPlan == null) return die("prepAnsible: no AccountPlan found for network: "+network.getUuid());
            planApps = planAppDAO.findByPlan(accountPlan.getPlan());
        } else {
            planApps = null;
        }

        // Copy database with new encryption key
        if (installRoles.stream().anyMatch(r->r.getName().startsWith("bubble-"))) {
            final String key = dbFilter.copyDatabase(fork, network, node, account, planApps, new File(bubbleFilesDir, "bubble.sql.gz"));
            ctx.put("dbEncryptionKey", key);

            // if this is a fork, and current server is local, then sage will be self
            if (fork && configuration.getThisNode().localIp4()) {
                ctx.put("sageNode", node.getUuid());
            } else {
                // otherwise, sage will be us, the node that is launching the new node
                ctx.put("sageNode", configuration.getThisNode().getUuid());
            }
        }

        for (AnsibleRole role : roles) {
            @Cleanup final InputStream roleStream = getTgzInputStream(node.getAccount(), role);
            final File roleTarball = toFile(new File(tarballDir, role.getName() + ".tgz"), roleStream);
            final File rolesDir = new File(automation, "roles");
            Tarball.unroll(roleTarball, rolesDir);

            if (role.hasConfig()) {
                final String roleName = role.getRoleName();
                final File roleDir = new File(abs(rolesDir)+"/"+roleName);
                final File varsDir = mkdirOrDie(new File(abs(roleDir)+"/vars"));
                final File varsMain = new File(varsDir, "main.yml");
                try (Writer w = new FileWriter(varsMain)) {
                    for (NameAndValue cfg : role.getConfig()) {
                        final String cfgName = cfg.getName();
                        final String rawVal = cfg.getValue();
                        final String value = HandlebarsUtil.apply(handlebars, rawVal, ctx);
                        if (value == null || value.trim().length() == 0) {
                            if (!role.hasOptionalConfigNames() || !role.isOptionalConfigName(cfgName)) {
                                errors.addViolation("err.role.config." + cfgName + ".required", "value for " + cfgName + " evaluated to empty string");
                            }
                            continue;
                        }
                        final boolean quote = value.contains(" ") && !value.trim().startsWith("[") && !value.trim().startsWith("{");
                        w.write(cfgName +": "+ (quote ? "\""+value+"\"" : value)+"\n");
                    }
                }
            }
        }
        return ctx;
    }

    public InputStream getTgzInputStream(String account, AnsibleRole role) {
        try {
            final String tgzB64 = role.getTgzB64();
            if (role.isTgzB64storage()) {
                return storageService.read(role.getAccount(), role.getTgzB64());

            } else {
                log.debug("getTgzInputStream: reading directly from tgzB64");
                return new ByteArrayInputStream(Base64.decode(tgzB64, DONT_GUNZIP));
            }
        } catch (Exception e) {
            return die("getTgzInputStream: "+e, e);
        }
    }

}
