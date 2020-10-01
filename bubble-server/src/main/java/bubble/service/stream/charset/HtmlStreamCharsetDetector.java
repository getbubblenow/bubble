/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream.charset;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bubble.service.stream.StreamConstants.MIN_BYTES_BEFORE_WRAP;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@Slf4j
public class HtmlStreamCharsetDetector extends HtmlCharsetDetector {

    public static final HtmlStreamCharsetDetector HTML_STREAM_CHARSET_DETECTOR = new HtmlStreamCharsetDetector();

    private static final Pattern HTML_CONTENT_TYPE_EQUIV_CHARSET
            = Pattern.compile("<meta\\s+http-equiv\\s*=\\s*\"Content-Type\"\\s+content=\"[/\\w]+\\s*;\\s*charset=([-\\w]+)\\s*\"\\s*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_META_CHARSET
            = Pattern.compile("<meta\\s+charset\\s*=\\s*\"([-\\w]+)\">", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_CLOSE_HEAD
            = Pattern.compile("</head[^>]*>", Pattern.CASE_INSENSITIVE);

    @Override public BubbleCharSet getCharSet(InputStream in, long size, boolean last) {
        final byte[] buffer = new byte[(int) MIN_BYTES_BEFORE_WRAP];
        int count;
        String fullData = null;
        try {
            final StringBuilder b = new StringBuilder();
            int bytesRead = 0;
            boolean zeroRead = false;
            while (bytesRead < size && (count = in.read(buffer, 0, readSize(size, buffer.length, bytesRead))) != -1) {
                if (count == 0) {
                    // reached end of multi-stream, if this is our second zero read, bail out
                    if (zeroRead) {
                        if (last) {
                            if (log.isDebugEnabled()) log.debug("getCharSet: exhausted stream and no match found, returning UTF-8");
                            return BubbleCharSet.UTF8;
                        }
                        if (log.isDebugEnabled()) log.debug("getCharSet: two zero reads, must be at end of multi-stream, returning null");
                        return null;
                    }
                    zeroRead = true;
                }
                final String data = new String(buffer, 0, count);
                b.append(data);
                fullData = b.toString();
                final Matcher metaMatcher = HTML_META_CHARSET.matcher(fullData);
                if (metaMatcher.find()) {
                    return BubbleCharSet.forCharSet(safeCharSet(metaMatcher.group(1)));
                }
                final Matcher equivMatcher = HTML_CONTENT_TYPE_EQUIV_CHARSET.matcher(fullData);
                if (equivMatcher.find()) {
                    return BubbleCharSet.forCharSet(safeCharSet(equivMatcher.group(1)));
                }
                final Matcher headCloseMatcher = HTML_CLOSE_HEAD.matcher(fullData);
                if (headCloseMatcher.find()) {
                    if (log.isDebugEnabled()) log.debug("getCharSet: found head closing tag before any charset specifier, returning UTF-8");
                    return BubbleCharSet.UTF8;
                }
            }
            if (last) {
                if (log.isDebugEnabled()) log.debug("getCharSet: exhausted stream and no match found, returning UTF-8");
                return BubbleCharSet.UTF8;
            }
            if (log.isDebugEnabled()) log.debug("getCharSet: exhausted stream and no match found, but more data may be coming, returning null");
            return null;

        } catch (Exception e) {
            log.error("getCharSet: io error, returning UTF-8: "+shortError(e));
            return BubbleCharSet.UTF8;
        }
    }

    private Charset safeCharSet(String csName) {
        try {
            return Charset.forName(csName);
        } catch (Exception e) {
            log.error("safeCharSet: invalid name, returning UTF-8: "+csName);
            return UTF8cs;
        }
    }

    private int readSize(long size, int bufsiz, int bytesRead) {
        return bytesRead + bufsiz < size ? bufsiz : (int) (bufsiz - (size - bytesRead));
    }

}
