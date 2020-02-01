package bubble.service.message;

import bubble.dao.app.AppMessageDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMessage;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.*;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

import static org.cobbzilla.util.string.StringUtil.EMPTY;

@Service @Slf4j
public class AppMessageService {

    public static final String MSG_PREFIX_APP = "app.";
    public static final String MSG_SUFFIX_DESCRIPTION = "description";
    public static final String MSG_SUFFIX_NAME = "name";
    public static final String MSG_SUFFIX_FIELD = "field.";
    public static final String MSG_SUFFIX_PARAM = "param.";
    public static final String MSG_SUFFIX_VIEW = "view.";
    public static final String MSG_SUFFIX_ACTION = "action.";
    public static final String MSG_SUFFIX_BUTTON = "button.";
    public static final String MSG_CONFIG = "config.";

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
                        ensureFieldNameAndDescription(props,msgPrefix + MSG_SUFFIX_FIELD, field.getName());
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

                if (cfg.hasConfigViews()) {
                    final String cfgKeyPrefix = msgPrefix + MSG_CONFIG;
                    for (AppConfigView configView : cfg.getConfigViews()) {
                        final String viewKey = cfgKeyPrefix + MSG_SUFFIX_VIEW + configView.getName();
                        if (!props.containsKey(viewKey)) props.setProperty(viewKey, configView.getName());

                        if (configView.hasColumns()) {
                            for (String column : configView.getColumns()) {
                                ensureFieldNameAndDescription(props, cfgKeyPrefix, column);
                            }
                        }

                        if (configView.hasActions()) {
                            for (AppConfigAction action : configView.getActions()) {
                                final String actionKey = cfgKeyPrefix + MSG_SUFFIX_ACTION + action.getName();
                                if (!props.containsKey(actionKey)) props.setProperty(actionKey, action.getName());

                                if (action.hasButton()) {
                                    final String buttonKey = cfgKeyPrefix + MSG_SUFFIX_BUTTON + action.getName();
                                    if (!props.containsKey(buttonKey)) props.setProperty(buttonKey, action.getButton());
                                }

                                if (action.hasParams()) {
                                    for (AppDataField param : action.getParams()) {
                                        ensureFieldNameAndDescription(props, cfgKeyPrefix, param.getName());
                                    }
                                }
                            }
                        }

                        if (configView.hasFields()) {
                            for (AppDataField field : configView.getFields()) {
                                ensureFieldNameAndDescription(props, cfgKeyPrefix, field.getName());
                            }
                        }
                    }
                }
            }
        }
        return props;
    }

    public void ensureFieldNameAndDescription(Properties props, String prefix, String name) {
        final String fieldKey = prefix + MSG_SUFFIX_FIELD + name;
        if (!props.containsKey(fieldKey)) props.setProperty(fieldKey, name);

        final String descKey = prefix + MSG_SUFFIX_FIELD + name + "." + MSG_SUFFIX_DESCRIPTION;
        if (!props.containsKey(descKey)) props.setProperty(descKey, EMPTY);
    }

}
