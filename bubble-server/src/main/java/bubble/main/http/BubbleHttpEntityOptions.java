package bubble.main.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.*;

@Slf4j
public class BubbleHttpEntityOptions extends BubbleHttpOptions {

    public String getRequestJson() {
        final String data = readStdin();
        // does the JSON contain any comments? scrub them before sending...
        if (data.contains("//") || data.contains("/*")) {
            try {
                return json(json(data, JsonNode.class, FULL_MAPPER_ALLOW_COMMENTS), COMPACT_MAPPER);
            } catch (Exception e) {
                log.warn("getRequestJson: error scrubbing comments from JSON, sending as-is: "+shortError(e));
            }
        }
        return data;
    }

    public static final String USAGE_CONTENT_TYPE = "Content-Type to send. Default is application/json";
    public static final String OPT_CONTENT_TYPE = "-C";
    public static final String LONGOPT_CONTENT_TYPE= "--content-type";
    @Option(name=OPT_CONTENT_TYPE, aliases=LONGOPT_CONTENT_TYPE, usage=USAGE_CONTENT_TYPE)
    @Getter @Setter private String contentType = APPLICATION_JSON;

    public ContentType contentType() { return ContentType.create(getContentType()); }

}
