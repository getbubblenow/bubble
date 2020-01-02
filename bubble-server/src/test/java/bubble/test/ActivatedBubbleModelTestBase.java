package bubble.test;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.dns.godaddy.GoDaddyDnsDriver;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.model.account.Account;
import bubble.model.account.ActivationRequest;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListener;
import org.cobbzilla.wizard.server.RestServer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.ROOT_USERNAME;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortErrorString;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.applyReflectively;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.CommandShell.hostname_short;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.scrubSpecial;

@Slf4j
public abstract class ActivatedBubbleModelTestBase extends BubbleModelTestBase {

    public static final String ROOT_PASSWORD = "password";
    public static final String ROOT_SESSION = "rootSession";
    public static final String ROOT_USER_VAR = "rootUser";

    protected Account admin;

    private boolean hasExistingDb = false;

    @Override public boolean doTruncateDb() { return false; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        // if server hbm2ddl mode is validate, do not delete node file
        if (server.getConfiguration().dbExists()) {
            hasExistingDb = true;
            log.info("beforeStart: not deleting "+abs(THIS_NODE_FILE)+" because DB exists");
        } else {
            // start fresh
            if (THIS_NODE_FILE.exists() && !THIS_NODE_FILE.delete()) {
                die("beforeStart: error deleting " + abs(THIS_NODE_FILE));
            }
        }
        super.beforeStart(server);
    }

    @Override protected String[] getSqlPostScripts() { return hasExistingDb ? null : super.getSqlPostScripts(); }

    @Override protected void modelTest(final String name, ApiRunner apiRunner) throws Exception {
        getApi().logout();
        final Account root = getApi().post(AUTH_ENDPOINT + EP_LOGIN, new LoginRequest(ROOT_USERNAME, ROOT_PASSWORD), Account.class);
        getApi().pushToken(root.getToken());
        apiRunner.addNamedSession(ROOT_SESSION, root.getToken());
        apiRunner.run(include(name));
    }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        //noinspection ResultOfMethodCallIgnored -- verifying entity topology has no cycles
        server.getConfiguration().getEntityClassesReverse();

        // Activate the system

        try {
            final BubbleConfiguration configuration = server.getConfiguration();
            final Handlebars handlebars = configuration.getHandlebars();
            final Map<String, Object> ctx = configuration.getEnvCtx();

            // expect domain to be the first domain listed in bubbleDomain.json
            final BubbleDomain domain = applyReflectively(handlebars, json(stream2string("models/system/bubbleDomain.json"), BubbleDomain[].class, FULL_MAPPER_ALLOW_COMMENTS)[0], ctx);

            final CloudService[] clouds = scrubSpecial(json(stream2string("models/system/cloudService.json"), JsonNode.class, FULL_MAPPER_ALLOW_COMMENTS), CloudService.class);

            // find public DNS service
            final CloudService dns = getPublicDns(ctx, clouds);

            // find storage service
            final CloudService storage = getNetworkStorage(ctx, clouds);

            // sanity check
            if (!dns.getName().equals(domain.getPublicDns())) die("onStart: DNS service mismatch: domain references "+domain.getPublicDns()+" but DNS service selected has name "+dns.getName());

            @Cleanup final ApiClientBase client = configuration.newApiClient();

            // if DB already exists, server has already been activated
            try {
                admin = client.put(AUTH_ENDPOINT + EP_ACTIVATE, new ActivationRequest()
                        .setName(ROOT_USERNAME)
                        .setPassword(ROOT_PASSWORD)
                        .setNetworkName(hostname_short())
                        .setDns(dns)
                        .setStorage(storage)
                        .setDomain(domain), Account.class);
            } catch (ValidationException e) {
                if (e.hasViolations() && e.getViolations().containsKey("err.activation.alreadyDone")) {
                    log.warn("onStart: activation already done, trying to login: " + shortErrorString(e));
                    admin = client.post(AUTH_ENDPOINT + EP_LOGIN, new LoginRequest(ROOT_USERNAME, ROOT_PASSWORD), Account.class);
                } else {
                    throw e;
                }
            }
            getApi().setConnectionInfo(client.getConnectionInfo());
            getApi().pushToken(admin.getApiToken());
            getApiRunner().getContext().put(ROOT_USER_VAR, admin);
            getApiRunner().addNamedSession(ROOT_SESSION, admin.getApiToken());

        } catch (Exception e) {
            die("onStart: "+e, e);
        }
        if (!hasExistingDb) super.onStart(server);
    }

    private CloudService findByTypeAndDriver(Map<String, Object> ctx,
                                             CloudService[] clouds,
                                             CloudServiceType type,
                                             Class<? extends CloudServiceDriver> driverClass) {
        final Handlebars handlebars = getConfiguration().getHandlebars();
        final List<CloudService> dnsServices = Arrays.stream(clouds)
                .filter(c -> c.getType() == type && c.getDriverClass().equals(driverClass.getName()))
                .collect(Collectors.toList());
        if (dnsServices.size() != 1) die("onStart: expected exactly one public dns service");
        return applyReflectively(handlebars, dnsServices.get(0), ctx);
    }
    private CloudService getPublicDns(Map<String, Object> ctx, CloudService[] clouds) {
        return findByTypeAndDriver(ctx, clouds, CloudServiceType.dns, getPublicDnsDriver());
    }
    protected Class<? extends CloudServiceDriver> getPublicDnsDriver() { return GoDaddyDnsDriver.class; }

    protected CloudService getNetworkStorage(Map<String, Object> ctx, CloudService[] clouds) {
        return findByTypeAndDriver(ctx, clouds, CloudServiceType.storage, getNetworkStorageDriver());
    }
    protected Class<? extends CloudServiceDriver> getNetworkStorageDriver() { return LocalStorageDriver.class; }

    @Override protected Class<? extends ModelSetupListener> getModelSetupListenerClass() { return BubbleModelSetupListener.class; }

}
