/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.main.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.kohsuke.args4j.Option;

import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
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
                log.warn("getRequestJson: error scrubbing comments from JSON, sending as-is: " + shortError(e));
            }
        }
        return data;
    }

    public InputStream getRequestStream() { return System.in; }

    public static final String USAGE_MULTIPART = "Send PUT or POST as a multipart-encoded file upload with this file name";
    public static final String OPT_MULTIPART = "-M";
    public static final String LONGOPT_MULTIPART= "--multipart";
    @Option(name=OPT_MULTIPART, aliases=LONGOPT_MULTIPART, usage=USAGE_MULTIPART)
    @Getter @Setter private String multipartFileName = null;
    public boolean hasMultipartFileName() { return !empty(multipartFileName); }


    public static final String USAGE_CONTENT_TYPE = "Content-Type to send. Default is application/json";
    public static final String OPT_CONTENT_TYPE = "-C";
    public static final String LONGOPT_CONTENT_TYPE= "--content-type";
    @Option(name=OPT_CONTENT_TYPE, aliases=LONGOPT_CONTENT_TYPE, usage=USAGE_CONTENT_TYPE)
    @Getter @Setter private String contentType = APPLICATION_JSON;

    public ContentType contentType() { return ContentType.create(getContentType()); }

}
