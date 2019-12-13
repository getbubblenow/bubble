package bubble.dao.remote;

import bubble.dao.app.AppDataDAOBase;
import bubble.model.app.AppData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.api.NotFoundException;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class RemoteAppDataDAO extends RemoteDAOBase implements AppDataDAOBase {

    @Override public String findValueByAppAndSiteAndKey(String app, String site, String key) {
        final String path = ME_ENDPOINT
                + EP_APPS + "/" + app
                + EP_SITES + "/" + site
                + EP_DATA + "/" + key;
        try {
            final JsonNode node = getApi().get(path, JsonNode.class);
            if (node.isArray()) {
                switch (node.size()) {
                    case 0: return null;
                    case 1: return json(node.get(0), AppData.class).getData();
                    default: log.warn("multiple results found!"); return null;
                }
            }
            return json(node, AppData.class).getData();

        } catch (NotFoundException notFound) {
            return null;
        } catch (Exception e) {
            return die("findValueByAppAndSiteAndKey: "+e, e);
        }
    }

}
