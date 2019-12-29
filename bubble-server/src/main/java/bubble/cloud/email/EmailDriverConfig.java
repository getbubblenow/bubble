package bubble.cloud.email;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.mail.sender.SmtpMailConfig;

import static bubble.ApiConstants.MESSAGE_RESOURCE_BASE;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true)
public class EmailDriverConfig extends SmtpMailConfig {

    public static final String DEFAULT_TEMPLATE_PATH = MESSAGE_RESOURCE_BASE+"[[locale]]/email";

    public EmailDriverConfig (SmtpMailConfig other) { copy(this, other); }

    @Getter @Setter private String templatePath = DEFAULT_TEMPLATE_PATH;

}
