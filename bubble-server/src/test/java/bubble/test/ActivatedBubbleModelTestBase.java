package bubble.test;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.dns.mock.MockDnsDriver;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.model.account.Account;
import bubble.model.boot.ActivationRequest;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListener;
import org.cobbzilla.wizard.server.RestServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.ROOT_USERNAME;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.applyReflectively;
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

    @Override public boolean doTruncateDb() { return false; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        final BubbleConfiguration configuration = server.getConfiguration();

        // set default domain
        final Map<String, String> env = configuration.getEnvironment();
        env.put("defaultDomain", getDefaultDomain());
        env.put("TEST_DEFAULT_DNS_CLOUD", "MockDns");
        configuration.setTestCloudModels(getCloudServiceModels());

        super.beforeStart(server);
    }

    public String getDefaultDomain() { return "example.com"; }

    @Override protected String[] getSqlPostScripts() { return hasExistingDb ? null : super.getSqlPostScripts(); }

    @Override protected void modelTest(final String name, ApiRunner apiRunner) throws Exception {
        getApi().logout();
        final Account root = getApi().post(AUTH_ENDPOINT + EP_LOGIN, new LoginRequest(ROOT_USERNAME, ROOT_PASSWORD), Account.class);
        if (empty(root.getToken())) die("modelTest: error logging in root user (was MFA configured in a previous test?): "+json(root));
        getApi().pushToken(root.getToken());
        apiRunner.addNamedSession(ROOT_SESSION, root.getToken());
        apiRunner.run(include(name));
    }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        //noinspection ResultOfMethodCallIgnored -- verifying entity topology has no cycles
        server.getConfiguration().getEntityClassesReverse();

        // Activate the system
        resetBubbleServer();
        try {
            final BubbleConfiguration configuration = server.getConfiguration();
            final Map<String, Object> ctx = configuration.getEnvCtx();

            // load all clouds
            CloudService[] clouds = new CloudService[0];
            for (String model : getCloudServiceModels()) {
                clouds = ArrayUtil.concat(clouds, scrubSpecial(json(stream2string(model), JsonNode.class, FULL_MAPPER_ALLOW_COMMENTS), CloudService.class));
            }

            // determine domain
            final BubbleDomain domain = getDomain(ctx, clouds);

            // find public DNS service
            final CloudService dns = getPublicDns(ctx, clouds);

            // find storage service
            final CloudService storage = getNetworkStorage(ctx, clouds);

            // sanity check
            if (!dns.getName().equals(domain.getPublicDns())) {
                die("onStart: DNS service mismatch: domain references "+domain.getPublicDns()+" but DNS service selected has name "+dns.getName());
            }

            @Cleanup final ApiClientBase client = configuration.newApiClient();

            // if DB already exists, server has already been activated
            try {
                admin = client.put(AUTH_ENDPOINT + EP_ACTIVATE, new ActivationRequest()
                        .setName(ROOT_USERNAME)
                        .setPassword(ROOT_PASSWORD)
                        .setNetworkName(hostname_short())
                        .addCloudConfig(dns)
                        .addCloudConfig(storage)
                        .setCreateDefaultObjects(false)
                        .setDomain(domain), Account.class);
            } catch (ValidationException e) {
                if (e.hasViolations() && e.getViolations().containsKey("err.activation.alreadyDone")) {
                    log.warn("onStart: activation already done, trying to login: " + shortError(e));
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

    protected List<String> getCloudServiceModels() {
        final ArrayList<String> models = new ArrayList<>();
        models.add("models/system/cloudService.json");
        models.add("models/system/cloudService_test.json");
        return models;
    }

    private BubbleDomain getDomain(Map<String, Object> ctx, CloudService[] clouds) {
        final Handlebars handlebars = getConfiguration().getHandlebars();
        final JsonNode domainsArray = json(HandlebarsUtil.apply(handlebars, stream2string("models/system/bubbleDomain.json"), ctx), JsonNode.class, FULL_MAPPER_ALLOW_COMMENTS);
        final BubbleDomain[] allDomains = scrubSpecial(domainsArray, BubbleDomain.class);
        return Arrays.stream(allDomains).filter(getDomainFilter(clouds)).findFirst().orElseGet(() -> die("getDomain: no candidate domain found"));
    }

    protected Predicate<? super BubbleDomain> getDomainFilter(CloudService[] clouds) { return bubbleDomain -> true; }

    private CloudService findByTypeAndDriver(Map<String, Object> ctx,
                                             CloudService[] clouds,
                                             CloudServiceType type,
                                             Class<? extends CloudServiceDriver> driverClass) {
        final Handlebars handlebars = getConfiguration().getHandlebars();
        final List<CloudService> dnsServices = Arrays.stream(clouds)
                .filter(c -> c.getType() == type && c.usesDriver(driverClass))
                .collect(Collectors.toList());
        if (dnsServices.size() != 1) die("onStart: expected exactly one public dns service");
        return applyReflectively(handlebars, dnsServices.get(0), ctx);
    }
    private CloudService getPublicDns(Map<String, Object> ctx, CloudService[] clouds) {
        return findByTypeAndDriver(ctx, clouds, CloudServiceType.dns, getPublicDnsDriver());
    }
    protected Class<? extends CloudServiceDriver> getPublicDnsDriver() { return MockDnsDriver.class; }

    protected CloudService getNetworkStorage(Map<String, Object> ctx, CloudService[] clouds) {
        return findByTypeAndDriver(ctx, clouds, CloudServiceType.storage, getNetworkStorageDriver());
    }
    protected Class<? extends CloudServiceDriver> getNetworkStorageDriver() { return LocalStorageDriver.class; }

    @Override protected Class<? extends ModelSetupListener> getModelSetupListenerClass() { return BubbleModelSetupListener.class; }

}
