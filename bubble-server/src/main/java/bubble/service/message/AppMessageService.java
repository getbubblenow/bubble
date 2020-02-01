package bubble.service.message;

import bubble.dao.app.AppMessageDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.app.config.AppDataAction;
import bubble.model.app.config.AppDataConfig;
import bubble.model.app.config.AppDataView;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

@Service @Slf4j
public class AppMessageService {

    public static final String MSG_PREFIX_APP = "app.";
    public static final String MSG_SUFFIX_DESCRIPTION = "description";
    public static final String MSG_SUFFIX_NAME = "name";
    public static final String MSG_SUFFIX_FIELD = "field.";
    public static final String MSG_SUFFIX_PARAM = "param.";
    public static final String MSG_SUFFIX_VIEW = "view.";
    public static final String MSG_SUFFIX_ACTION = "action.";

    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppMessageDAO appMessageDAO;

    public Properties loadAppMessages(Account account, String locale) {
        final Properties props = new Properties();
        for (BubbleApp app : appDAO.findByAccount(account.getUuid())) {
            final AppMessage appMessage = appMessageDAO.findByAccountAndAppBestEffort(account.getUuid(), app.getUuid(), locale);
            final Map<String, String> messages = NameAndValue.toMap(appMessage == null  ? null : appMessage.getMessages());

            final String msgPrefix = MSG_PREFIX_APP + app.getName() + ".";
            for (Map.Entry<String, String> message : messages.entrySet()) {
                props.setProperty(msgPrefix+message.getKey(), message.getValue());
            }

            // Check for 'name' message, if not found use default
            final String nameKey = msgPrefix + MSG_SUFFIX_NAME;
            if (!props.containsKey(nameKey)) props.setProperty(nameKey, app.getName());

            // Check for 'description' message, if not found use default
            final String descriptionKey = msgPrefix + MSG_SUFFIX_DESCRIPTION;
            if (!props.containsKey(descriptionKey)) props.setProperty(descriptionKey, app.getDescription());

            if (app.hasDataConfig()) {
                final AppDataConfig cfg = app.getDataConfig();

                // Check for field messages
                if (cfg.hasFields()) {
                    for (EntityFieldConfig field : cfg.getFields()) {
                        final String fieldKey = msgPrefix + MSG_SUFFIX_FIELD + field.getName();
                        if (!props.containsKey(fieldKey)) props.setProperty(fieldKey, field.getName());
                    }
                }

                // Check for view messages
                if (cfg.hasViews()) {
                    for (AppDataView view : cfg.getViews()) {
                        final String viewKey = msgPrefix + MSG_SUFFIX_VIEW + view.getName();
                        if (!props.containsKey(viewKey)) props.setProperty(viewKey, view.getName());
                    }
                }

                // Check for param messages
                if (cfg.hasParams()) {
                    for (EntityFieldConfig param : cfg.getParams()) {
                        final String paramKey = msgPrefix + MSG_SUFFIX_PARAM + param.getName();
                        if (!props.containsKey(paramKey)) props.setProperty(paramKey, param.getName());
                    }
                }

                // Check for action messages
                if (cfg.hasActions()) {
                    for (AppDataAction action : cfg.getActions()) {
                        final String actionKey = msgPrefix + MSG_SUFFIX_ACTION + action.getName();
                        if (!props.containsKey(actionKey)) props.setProperty(actionKey, action.getName());
                    }
                }
            }
        }
        return props;
    }

}
