package bubble.cloud.sms;

import bubble.cloud.auth.RenderedMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor @Accessors(chain=true)
public class RenderedSms implements RenderedMessage {

    public RenderedSms(Map<String, Object> ctx) { this.ctx = ctx; }

    @Getter private String uuid = randomUUID().toString();
    @Getter private long ctime = now();

    @Getter @Setter private String fromNumber;
    @Getter @Setter private String toNumber;
    @Getter @Setter private String text;
    @Getter @Setter private Map<String, Object> ctx;

}
