package bubble.notify;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.json.JsonSerializableException;

@NoArgsConstructor @Accessors(chain=true)
public class SynchronousNotificationReply {

    @Getter @Setter private String notificationId;

    @Getter @Setter private JsonNode response;
    public boolean hasResponse () { return response != null; }

    @Getter @Setter private JsonSerializableException exception;
    public boolean hasException () { return exception != null; }

}
