package bubble.service.stream.charset;

import java.io.InputStream;

import static bubble.service.stream.charset.HtmlCharsetDetector.htmlCharSetDetector;
import static org.cobbzilla.util.http.HttpContentTypes.isHtml;

public interface CharsetDetector {

    CharsetDetector SKIP_CHARSET_DETECTION = new SkipCharsetDetection();

    static CharsetDetector charSetDetectorForContentType(String contentType) {
        if (isHtml(contentType)) return htmlCharSetDetector(contentType);
        return SKIP_CHARSET_DETECTION;
    }

    BubbleCharSet getCharSet(InputStream in, long size, boolean last);

    class SkipCharsetDetection implements CharsetDetector {
        @Override public BubbleCharSet getCharSet(InputStream in, long size, boolean last) { return BubbleCharSet.RAW; }
    }

}
