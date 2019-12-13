package bubble.main.http;

import lombok.Getter;
import lombok.Setter;
import org.apache.http.entity.ContentType;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;

public class BubbleHttpEntityOptions extends BubbleHttpOptions {

    public String getRequestJson() { return readStdin(); }

    public static final String USAGE_CONTENT_TYPE = "Content-Type to send. Default is application/json";
    public static final String OPT_CONTENT_TYPE = "-C";
    public static final String LONGOPT_CONTENT_TYPE= "--content-type";
    @Option(name=OPT_CONTENT_TYPE, aliases=LONGOPT_CONTENT_TYPE, usage=USAGE_CONTENT_TYPE)
    @Getter @Setter private String contentType = APPLICATION_JSON;

    public ContentType contentType() { return ContentType.create(getContentType()); }

}
