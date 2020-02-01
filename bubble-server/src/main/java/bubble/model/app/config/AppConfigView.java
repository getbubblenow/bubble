package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

public class AppConfigView {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigViewType type;
    @Getter @Setter private String[] columns;
    @Getter @Setter private AppConfigAction[] actions;
    @Getter @Setter private AppDataField[] fields;

}
