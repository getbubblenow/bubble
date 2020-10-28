package bubble.cloud.email;

import lombok.Getter;
import lombok.Setter;

import static bubble.cloud.email.EmailDriverConfig.DEFAULT_TEMPLATE_PATH;

public class EmailApiDriverConfig {

    @Getter @Setter private String templatePath = DEFAULT_TEMPLATE_PATH;

}
