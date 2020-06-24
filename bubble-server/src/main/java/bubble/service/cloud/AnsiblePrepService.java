/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.ApiConstants;
import bubble.cloud.compute.AnsibleRole;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanAppDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
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

@Service @Slf4j
public class AnsiblePrepService {

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
        ctx.put("nodeSize", computeDriver.getSize(node.getSizeType()));
        if (restoreKey != null) {
            ctx.put("restoreKey", restoreKey);
            ctx.put("restoreTimeoutSeconds", RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS);
        }
        ctx.put("sslPort", network.getSslPort());
        ctx.put("publicBaseUri", network.getPublicUri());
        ctx.put("cert_validation_host", configuration.getCertValidationHost());
        ctx.put("support", configuration.getSupport());
        ctx.put("appLinks", configuration.getAppLinks());

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
        final List<BubblePlanApp> planApps;
        if (configuration.paymentsEnabled()) {
            final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(account.getUuid(), network.getUuid());
            if (accountPlan == null) return die("prepAnsible: no AccountPlan found for network: "+network.getUuid());
            planApps = planAppDAO.findByPlan(accountPlan.getPlan());
        } else {
            planApps = null;
        }

        // Copy database with new encryption key
        final String key = dbFilter.copyDatabase(fork, network, node, account, planApps, new File(bubbleFilesDir, "bubble.sql.gz"));
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
                        log.debug("prepAnsible["+roleName+"]: setting "+rawVal+" => "+cfgName+": "+cfgValue);
                        w.write(cfgName +": "+ cfgValue +"\n");
                    }
                }
            }
        }
        return ctx;
    }

}
