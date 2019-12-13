package bubble.test;

import bubble.cloud.CloudServiceType;
import bubble.model.account.Account;
import bubble.model.account.ActivationRequest;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
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
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.service.boot.StandardSelfNodeService.THIS_NODE_FILE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.applyReflectively;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.CommandShell.hostname_short;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.scrubSpecial;

public abstract class ActivatedBubbleModelTestBase extends BubbleModelTestBase {

    public static final String ROOT_PASSWORD = "password";
    public static final String ROOT_SESSION = "rootSession";

    protected Account admin;

    @Override public boolean doTruncateDb() { return false; }

    @Override public void beforeStart(RestServer<BubbleConfiguration> server) {
        // always start fresh
        if (THIS_NODE_FILE.exists() && !THIS_NODE_FILE.delete()) {
            die("beforeStart: error deleting "+abs(THIS_NODE_FILE));
        }
        super.beforeStart(server);
    }

    @Override protected void modelTest(final String name, ApiRunner apiRunner) throws Exception {
        getApi().logout();
        final Account root = getApi().post(AUTH_ENDPOINT + EP_LOGIN, new LoginRequest(Account.ROOT_USERNAME, ROOT_PASSWORD), Account.class);
        getApi().pushToken(root.getToken());
        apiRunner.addNamedSession("rootSession", root.getToken());
        apiRunner.run(include(name));
    }

    @Override public void onStart(RestServer<BubbleConfiguration> server) {
        // Activate the system
        try {
            final BubbleConfiguration configuration = server.getConfiguration();
            final Handlebars handlebars = configuration.getHandlebars();
            final Map<String, Object> ctx = configuration.getEnvCtx();

            // expect domain to be the first domain listed in bubbleDomain.json
            final BubbleDomain domain = applyReflectively(handlebars, json(stream2string("models/system/bubbleDomain.json"), BubbleDomain[].class, FULL_MAPPER_ALLOW_COMMENTS)[0], ctx);

            final CloudService[] clouds = scrubSpecial(json(stream2string("models/system/cloudService.json"), JsonNode.class, FULL_MAPPER_ALLOW_COMMENTS), CloudService.class);

            // expect public dns to be the LAST DNS cloud service listed in cloudService.json
            final List<CloudService> dnsServices = Arrays.stream(clouds)
                    .filter(c -> c.getType() == CloudServiceType.dns)
                    .collect(Collectors.toList());
            if (dnsServices.isEmpty()) die("onStart: no public DNS service found");
            final CloudService dns = applyReflectively(handlebars, dnsServices.get(dnsServices.size()-1), ctx);

            // find storage service
            final CloudService storage = getNetworkStorage(ctx, clouds);

            // sanity check
            if (!dns.getName().equals(domain.getPublicDns())) die("onStart: DNS service mismatch");

            @Cleanup final ApiClientBase client = configuration.newApiClient();
            admin = client.put(AUTH_ENDPOINT + EP_ACTIVATE, new ActivationRequest()
                    .setName(Account.ROOT_USERNAME)
                    .setPassword(ROOT_PASSWORD)
                    .setNetworkName(hostname_short())
                    .setDns(dns)
                    .setStorage(storage)
                    .setDomain(domain), Account.class);
            getApi().setConnectionInfo(client.getConnectionInfo());
            getApi().pushToken(admin.getApiToken());
            getApiRunner().addNamedSession(ROOT_SESSION, admin.getApiToken());

        } catch (Exception e) {
            die("onStart: "+e, e);
        }
        super.onStart(server);
    }

    protected CloudService getNetworkStorage(Map<String, Object> ctx, CloudService[] clouds) {
        final Handlebars handlebars = getConfiguration().getHandlebars();
        final List<CloudService> storageServices = Arrays.stream(clouds)
                .filter(c -> c.getType() == CloudServiceType.storage && c.getName().equals(getNetworkStorageName()))
                .collect(Collectors.toList());
        if (storageServices.size() != 1) die("onStart: expected exactly one network storage service");
        return applyReflectively(handlebars, storageServices.get(0), ctx);
    }

    protected String getNetworkStorageName() { return LOCAL_STORAGE; }

    @Override protected Class<? extends ModelSetupListener> getModelSetupListenerClass() { return BubbleModelSetupListener.class; }

}
