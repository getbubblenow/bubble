/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.ApiConstants;
import bubble.cloud.compute.AnsibleRole;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanAppDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.LaunchType;
import bubble.server.BubbleConfiguration;
import bubble.service.dbfilter.DatabaseFilterService;
import com.github.jknack.handlebars.Handlebars;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.wizard.server.config.ErrorApiConfiguration;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.service.backup.RestoreService.RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.mkdirOrDie;
import static org.cobbzilla.util.io.StreamUtil.copyClasspathDirectory;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.OPENAPI_DISABLED;

@Service @Slf4j
public class AnsiblePrepService {

    private static final int MIN_OPEN_API_MEMORY = 4096;

    @Autowired private DatabaseFilterService dbFilter;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanAppDAO planAppDAO;

    public Map<String, Object> prepAnsible(TempDir automation,
                                           File bubbleFilesDir,
                                           Account account,
                                           BubbleNetwork network,
                                           BubbleNode node,
                                           ComputeServiceDriver computeDriver,
                                           ValidationResult errors,
                                           boolean fork,
                                           LaunchType launchType,
                                           String restoreKey) throws IOException {
        final BubbleConfiguration c = configuration;

        final AnsibleInstallType installType = network.getInstallType();

        final String[] installRoles = installType == AnsibleInstallType.sage
                ? ApiConstants.ROLES_SAGE
                : ApiConstants.ROLES_NODE;

        final Map<String, Object> ctx = new HashMap<>();
        final Handlebars handlebars = c.getHandlebars();
        ctx.put("configuration", configuration);
        ctx.put("fork", fork);
        ctx.put("installType", installType.name());
        ctx.put("isNode", installType == AnsibleInstallType.node);
        ctx.put("isSage", installType == AnsibleInstallType.sage);

        final ComputeNodeSize nodeSize = computeDriver.getSize(node.getSizeType());
        ctx.put("nodeSize", nodeSize);
        ctx.put("jvmMaxRamMB", jvmMaxRam(nodeSize, installType));
        if (restoreKey != null) {
            ctx.put("restoreKey", restoreKey);
            ctx.put("restoreTimeoutSeconds", RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS);
        }
        ctx.put("sslPort", network.getSslPort());
        ctx.put("publicBaseUri", network.getPublicUri());
        ctx.put("support", configuration.getSupport());
        ctx.put("appLinks", configuration.getAppLinks());
        computeDriver.addLaunchContext(ctx, "bubble_deploy_");

        if (shouldEnableOpenApi(installType, nodeSize)) {
            ctx.put("openapi_contact_email", configuration.getOpenApi().getContactEmail());
        } else {
            ctx.put("openapi_contact_email", OPENAPI_DISABLED);
        }

        if (network.sendErrors() && configuration.hasErrorApi()) {
            final ErrorApiConfiguration errorApi = configuration.getErrorApi();
            ctx.put("error_url", errorApi.getUrl());
            ctx.put("error_key", errorApi.getKey());
            ctx.put("error_env", node.getFqdn());
        }

        ctx.put("network", network);
        ctx.put("node", node);
        ctx.put("roles", installRoles);
        ctx.put("testMode", !fork && configuration.testMode());

        // Determine which apps should be copied based on plan
        final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());
        if (accountPlan == null) return die("prepAnsible: no AccountPlan found for network: "+network.getUuid());
        final List<BubblePlanApp> planApps = planAppDAO.findByPlan(accountPlan.getPlan());

        // Copy database with new encryption key
        final String key = dbFilter.copyDatabase(fork, launchType, network, node, account, planApps, new File(bubbleFilesDir, "bubble.sql.gz"));
        ctx.put("dbEncryptionKey", key);

        // if this is a fork, and current server is local, then sage will be self
        if (fork && configuration.getThisNode().localIp4()) {
            ctx.put("sageNode", node);
        } else {
            // otherwise, sage will be us, the node that is launching the new node
            ctx.put("sageNode", configuration.getThisNode());
        }

        final File rolesDir = new File(automation, "roles");
        for (String roleName : installRoles) {
            final TempDir roleTemp = copyClasspathDirectory("ansible/roles/"+roleName);
            final File roleDir = new File(rolesDir, roleName);
            copyDirectory(roleTemp, roleDir);
            final File bubbleRoleJson = new File(abs(roleDir)+"/files/bubble_role.json");
            if (bubbleRoleJson.exists()) {
                final File varsDir = mkdirOrDie(new File(abs(roleDir)+"/vars"));
                final File varsMain = new File(varsDir, "main.yml");
                final AnsibleRole role = json(FileUtil.toStringOrDie(bubbleRoleJson), AnsibleRole.class);
                try (Writer w = new FileWriter(varsMain)) {
                    for (NameAndValue cfg : role.getConfig()) {
                        final String cfgName = cfg.getName();
                        final String rawVal = cfg.getValue();
                        final String value = HandlebarsUtil.apply(handlebars, rawVal, ctx, '[', ']');
                        if (value == null || value.trim().length() == 0) {
                            if (!role.hasOptionalConfigNames() || !role.isOptionalConfigName(cfgName)) {
                                errors.addViolation("err.role.config." + cfgName + ".required", "value for " + cfgName + " evaluated to empty string");
                            }
                            continue;
                        }
                        final boolean quote = value.contains(" ") && !value.trim().startsWith("[") && !value.trim().startsWith("{");
                        final String cfgValue = quote ? "\"" + value + "\"" : value;

                        // config values cannot contain certain chars to avoid shell injection attacks
                        if (cfgValue.contains(";") || cfgValue.contains("&") || cfgValue.contains("$") ) {
                            errors.addViolation("err.role.config."+cfgName+".invalid", "Invalid character in config value for param "+cfgName+": "+cfgValue, cfgValue);
                        } else {
                            log.debug("prepAnsible[" + roleName + "]: setting " + rawVal + " => " + cfgName + ": " + cfgValue);
                            w.write(cfgName + ": " + cfgValue + "\n");
                        }
                    }
                }
            }
        }
        return ctx;
    }

    private int jvmMaxRam(ComputeNodeSize nodeSize, AnsibleInstallType installType) {
        final int memoryMB = nodeSize.getMemoryMB();
        if (installType == AnsibleInstallType.sage) return (int) (((double) memoryMB) * 0.6d);
        if (memoryMB >= 4096) return (int) (((double) memoryMB) * 0.6d);
        if (memoryMB >= 2048) return (int) (((double) memoryMB) * 0.5d);
        if (memoryMB >= 1024) return (int) (((double) memoryMB) * 0.24d);
        // no nodes are this small, API probably would not start, not enough memory
        return (int) (((double) memoryMB) * 0.19d);
    }

    private boolean shouldEnableOpenApi(AnsibleInstallType installType, ComputeNodeSize nodeSize) {
        // to enable OpenAPI on a deployed node:
        // - it must already be enabled on the current bubble
        // - the bubble being launched must be a sage or have 4GB+ memory
        return configuration.hasOpenApi() &&
                (installType == AnsibleInstallType.sage || nodeSize.getMemoryMB() >= MIN_OPEN_API_MEMORY);
    }

}
