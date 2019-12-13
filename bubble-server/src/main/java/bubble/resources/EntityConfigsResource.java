package bubble.resources;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.resources.AbstractEntityConfigsResource;
import org.cobbzilla.wizard.server.config.StaticHttpConfiguration;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;
import java.io.File;

import static bubble.ApiConstants.ENTITY_CONFIGS_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.string.StringUtil.packagePath;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Path(ENTITY_CONFIGS_ENDPOINT)
@Service @Slf4j
public class EntityConfigsResource extends AbstractEntityConfigsResource {

    @Autowired private AccountDAO accountDAO;
    @Getter(AccessLevel.PROTECTED) @Autowired private BubbleConfiguration configuration;

    @Override protected boolean authorized(ContainerRequest ctx) {
        if (!accountDAO.activated()) return true;
        final Account account = userPrincipal(ctx);
        return !account.suspended();
    }

    @Override protected File getLocalConfig(EntityConfig config) {

        final StaticHttpConfiguration staticAssets = getConfiguration().getStaticAssets();
        if (staticAssets == null || !staticAssets.hasLocalOverride()) return null;

        final File localOverride = staticAssets.getLocalOverride();
        try {
            if (abs(localOverride).endsWith("/bubble-server/src/main/resources/site")) {
                // we are in a development environment
                return new File(abs(localOverride.getParentFile())
                        + "/" + ENTITY_CONFIG_BASE
                        + "/" + packagePath(config.getClassName()) + ".json");
            } else {
                // staging/production: localOverride is the site dir
                return new File(abs(localOverride)
                        + "/" + ENTITY_CONFIG_BASE
                        + "/" + packagePath(config.getClassName()) + ".json");
            }
        } catch (Exception e) {
            final String msg = "error getting config for: " + config;
            log.error(msg);
            return die(msg, e);
        }
    }

}
