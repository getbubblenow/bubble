package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

public class AppConfigAction {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigActionType type;
    @Getter @Setter private String when;
    @Getter @Setter private String view;

}
