package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;

public class AppDataField extends EntityFieldConfig {

    @Getter @Setter private Boolean customFormat;
    @Getter @Setter private String when;

}
