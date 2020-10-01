/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream.charset;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.service.stream.charset.HtmlStreamCharsetDetector.HTML_STREAM_CHARSET_DETECTOR;

@Slf4j
public abstract class HtmlCharsetDetector implements CharsetDetector {

    private static final Map<String, HtmlCharsetDetector> detectors = new ConcurrentHashMap<>(10);

    public static final String CONTENT_TYPE_CHARSET = "charset=";

    public static CharsetDetector htmlCharSetDetector(String contentType) {
        return detectors.computeIfAbsent(contentType, ct -> {
            final int csPos = ct.indexOf(CONTENT_TYPE_CHARSET);
            if (csPos == -1) return HTML_STREAM_CHARSET_DETECTOR;
            final String charsetName = ct.substring(csPos + CONTENT_TYPE_CHARSET.length());
            try {
                final Charset cs = Charset.forName(charsetName);
                return new HtmlContentTypeCharSet(cs);
            } catch (Exception e) {
                log.error("htmlCharSetDetector: invalid charset, returning HtmlStreamCharsetDetector: "+charsetName);
                return HTML_STREAM_CHARSET_DETECTOR;
            }
        });
    }

    @AllArgsConstructor
    public static class HtmlContentTypeCharSet extends HtmlCharsetDetector {
        private final Charset charset;
        @Override public BubbleCharSet getCharSet(InputStream in, long size, boolean last) {
            return BubbleCharSet.forCharSet(charset);
        }
    }

}
