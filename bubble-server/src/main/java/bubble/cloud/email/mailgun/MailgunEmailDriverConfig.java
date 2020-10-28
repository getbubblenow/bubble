package bubble.cloud.email.mailgun;

import bubble.cloud.email.EmailApiDriverConfig;
import lombok.Getter;
import lombok.Setter;

public class MailgunEmailDriverConfig extends EmailApiDriverConfig {

    @Getter @Setter private String domain;

}
