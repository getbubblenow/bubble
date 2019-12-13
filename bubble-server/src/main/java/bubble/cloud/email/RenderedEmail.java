package bubble.cloud.email;

import bubble.cloud.auth.RenderedMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.mail.SimpleEmailMessage;

import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor
public class RenderedEmail extends SimpleEmailMessage implements RenderedMessage {

    @Getter private long ctime = now();
    @Getter private String uuid = randomUUID().toString();
    @Getter @Setter private Map<String, Object> ctx;

    public RenderedEmail (Map<String, Object> ctx) { this.ctx = ctx; }

    public RenderedEmail (SimpleEmailMessage email) { copy(this, email); }

}
